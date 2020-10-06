package services

import java.sql.Timestamp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpCharset, HttpRequest, MediaRange, MediaType, StatusCodes}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Sink}
import akka.util.ByteString
import models.{PlutoCommission, PlutoCommissionRow, ProductionOffice}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import services.migrationcomponents.{HttpHelper, PlutoCommissionSource, VSUserCache}
import play.api.libs.json.{JsArray, JsValue, Json}
import slick.jdbc.PostgresProfile
import slick.lifted.{TableQuery, Tag}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.util.{Success, Try}

object DataMigration {
  val dateFormatter = DateTimeFormatter.ISO_DATE

  /**
   * look up the values for a given field in the metadata document
   * @param doc JsValue representing the json MetadataDocument
   * @param fieldName field to search for
   * @return None if no field was found. Otherwise, a sequence of zero or more string values for the field.
   */
  def valuesForField(doc:JsValue, fieldName:String):Option[IndexedSeq[String]] = {
    val values = for {
      timespan <- (doc \ "timespan").as[JsArray].value
      fieldBlock <- (timespan \ "field").as[JsArray].value if (fieldBlock \ "name").as[String] == fieldName
      valueBlock <- (fieldBlock \ "value").as[JsArray].value
    } yield (valueBlock \ "value").as[String]

    if(values.isEmpty) {
      None
    } else {
      Some(values.toIndexedSeq)
    }
  }
}

class DataMigration (sourceBasePath:String, sourceUser:String, sourcePasswd:String, sourceSystemId:String, userCache:VSUserCache)
                    (implicit system:ActorSystem, mat:Materializer, dbConfigProvider:DatabaseConfigProvider) {
  private implicit val http:akka.http.scaladsl.HttpExt = Http()
  private implicit val dispatcher = system.dispatcher
  private final val logger = LoggerFactory.getLogger(getClass)

  private lazy val db = dbConfigProvider.get[PostgresProfile].db

  def requestOriginalRecord(vsId:String) = {
    HttpHelper.requestJson(s"$sourceBasePath/API/collection/$vsId/metadata", sourceUser, sourcePasswd).map({
      case Left(errStr)=>
        logger.error(s"Could not load metadata for collection $vsId: $errStr")
        throw new RuntimeException("Could not load metadata")
      case Right(jsonContent)=>jsonContent
    })
  }

  def performCommissionFieldUpdate(recordId:Int, updatedScheduledCompletion:Timestamp, updatedOwner:String, updatedNotes:Option[String],
                                   updatedProductionOffice:ProductionOffice.Value, updatedOriginalTitle:Option[String]) = {
    import models.ProductionOfficeMapper._
    db.run {
      DBIO.seq(
        TableQuery[PlutoCommissionRow].filter(_.id===recordId).map(_.scheduledCompletion).update(updatedScheduledCompletion),
        TableQuery[PlutoCommissionRow].filter(_.id===recordId).map(_.owner).update(updatedOwner),
        TableQuery[PlutoCommissionRow].filter(_.id===recordId).map(_.notes).update(updatedNotes),
        TableQuery[PlutoCommissionRow].filter(_.id===recordId).map(_.productionOffice).update(updatedProductionOffice),
        TableQuery[PlutoCommissionRow].filter(_.id===recordId).map(_.originalTitle).update(updatedOriginalTitle)
      )
    }
  }

  def updateCommission(itemToUpdate: PlutoCommission):Future[Option[PlutoCommission]] =
    itemToUpdate.collectionId match {
      case Some(collId)=>
        val vsid = s"$sourceSystemId-$collId"
        requestOriginalRecord(vsid).flatMap(originalRecord=>{
          /*
          fields to update:
            def originalCommissionerName = column[Option[String]]("s_original_commissioner")
            def scheduledCompletion = column[Timestamp]("t_scheduled_completion")
            def owner = column[String]("s_owner")
            def notes = column[Option[String]]("s_notes")
            def productionOffice = column[ProductionOffice.Value]("s_production_office")
            def originalTitle = column[Option[String]]("s_original_title")
           */
          val updatedScheduledCompletionStr = DataMigration
            .valuesForField(originalRecord, "gnm_commission_scheduledcompletion")
            .flatMap(_.headOption)
            .getOrElse("2020-01-01")
          val scheduledCompletionDate = LocalDate.parse(updatedScheduledCompletionStr, DataMigration.dateFormatter)
          val updatedScheduledCompletion:Timestamp = Timestamp.valueOf(scheduledCompletionDate.atTime(0,0))
          val updatedOwnerId:String =
            DataMigration
              .valuesForField(originalRecord, "gnm_commission_owner")
              .map(_.map(str=>Try { str.toInt }).collect({case Success(intVal)=>intVal}))
              .map(_.map(userCache.lookup).collect({case Some(username)=>username}))
              .map(_.mkString("|"))
              .getOrElse("system")

          val updatedNotes:Option[String] = DataMigration
            .valuesForField(originalRecord, "gnm_commission_notes")
            .map(_.mkString("\n\n"))
          val updatedProductionOffice:ProductionOffice.Value = ProductionOffice.withName(
            DataMigration.valuesForField(originalRecord, "gnm_commission_production_office")
              .flatMap(_.headOption)
              .getOrElse("UK")
          )

          val updatedOriginalTitle:Option[String] = DataMigration
            .valuesForField(originalRecord, "title")
            .flatMap(_.headOption)

          performCommissionFieldUpdate(itemToUpdate.id.get,updatedScheduledCompletion, updatedOwnerId,updatedNotes, updatedProductionOffice, updatedOriginalTitle)
            .map(_=>Some(itemToUpdate))
        })

      case None=>
        logger.warn(s"Can't migrate entry $itemToUpdate as it has no vidispine ID")
        Future(None)
  }

  def migrateCommissionsData:Future[Int] = {
    val counterSinkFact = Sink.fold[Int, Any](0)((counter, _)=>counter+1)

    val graph = GraphDSL.create(counterSinkFact) { implicit builder=> counterSink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val commissions =  builder.add(new PlutoCommissionSource(dbConfigProvider, 100))
      commissions.out.mapAsync(4)(updateCommission) ~> counterSink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }
}
