package services

import java.io.File
import java.nio.file.Files
import java.sql.Timestamp
import java.time.{Instant, LocalDate}
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpCharset, HttpRequest, MediaRange, MediaType, StatusCodes}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Sink}
import akka.util.ByteString
import models.{FileAssociationRow, FileEntry, PlutoCommission, PlutoCommissionRow, ProductionOffice, StorageEntry, StorageEntryHelper}
import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import services.migrationcomponents.{DBObjectSource, HttpHelper, LinkVStoPL, MultipleCounter, PlutoCommissionSource, ProjectFileExistsSwitch, ProjectFileResult, ProjectNoFilesSource, VSProjectSource, VSUserCache}
import play.api.libs.json.{JsArray, JsValue, Json}
import slick.jdbc.PostgresProfile
import slick.lifted.{TableQuery, Tag}
import slick.jdbc.PostgresProfile.api._
import slick.lifted

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

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

  def testNoFilesSearch(implicit system:ActorSystem, mat:Materializer, dbConfigProvider:DatabaseConfigProvider) = {
    val counterSink = Sink.fold[Int, Any](0)((counter, _)=>counter+1)

    val graph = GraphDSL.create(counterSink) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new ProjectNoFilesSource(dbConfigProvider, 50))
      src ~> sink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }
}

class DataMigration (sourceBasePath:String, sourceUser:String, sourcePasswd:String, sourceSystemId:String, userCache:VSUserCache)
                    (implicit system:ActorSystem, mat:Materializer, dbConfigProvider:DatabaseConfigProvider) {
  private implicit val http:akka.http.scaladsl.HttpExt = Http()
  private implicit val dispatcher = system.dispatcher
  private final val logger = LoggerFactory.getLogger(getClass)

  private lazy implicit val db = dbConfigProvider.get[PostgresProfile].db

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
        logger.info(s"Running collection migrate on ${itemToUpdate.title} ($vsid)...")
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
            .map(_=>{
              logger.info(s"Migration of $vsid complete")
              Some(itemToUpdate)
            })
        }).recover({
          case err:RuntimeException=>
            logger.error(s"Collection $collId failed")
            None
        })

      case None=>
        logger.warn(s"Can't migrate entry $itemToUpdate as it has no vidispine ID")
        Future(None)
  }

  /**
    * returns a factory for a basic counting sink that materializes the total number of items that went through it
    * @return the Sink[]
    */
  private def counterSinkFact = Sink.fold[Int, Any](0)((counter, _)=>counter+1)

  /**
    * kicks off the migration of commissions data, i.e. filling in the extra fields that are not present in the ported
    * records from the old system
    * @return a Future, containing the number of records processed
    */
  def migrateCommissionsData:Future[Int] = {
    val graph = GraphDSL.create(counterSinkFact) { implicit builder=> counterSink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val commissions =  builder.add(new PlutoCommissionSource(dbConfigProvider, 100))
      commissions.out.mapAsync(4)(updateCommission) ~> counterSink
      ClosedShape
    }

    logger.info("Starting up migration stream...")
    RunnableGraph.fromGraph(graph).run()
  }

  /**
    * kicks off the migration of projects data, i.e. cross-checking from the VS system and either updating or
    * creating records as necessary
    * @param forceProjectType project type to apply to newly created records
    * @return a Future, containing the number of records processed
    */
  def migrateProjectsData(forceProjectType:Int):Future[Int] = {
    val graph = GraphDSL.create(counterSinkFact) { implicit builder=> counterSink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val vsProjects = builder.add(new VSProjectSource(sourceBasePath, sourceUser, sourcePasswd, pageSize=40))
      val projectLinker = builder.add(new LinkVStoPL(forceProjectType, userCache))

      vsProjects ~> projectLinker
      projectLinker.mapAsync(4)(_.save) ~> counterSink
      ClosedShape
    }

    logger.info("Starting up migration stream...")
    RunnableGraph.fromGraph(graph).run()
  }

  /**
   * copy file from A to B
   * @param from [[ProjectFileResult]] representing the file(s) to copy
   * @param destPath path to copy them to (string)
   * @return the incoming [[ProjectFileResult]] if all were successful, otherwise throws a RuntimeException
   */
  def copyFiles(from:ProjectFileResult, destPath:String):ProjectFileResult = {
    val copyResults = from.filePaths.map(filepath=>Try {
      val destFile = FilenameUtils.concat(destPath,FilenameUtils.getName(filepath))
      logger.debug(s"Copying $filepath to $destFile")
      FileUtils.copyFile(
        new File(filepath),
        new File(destFile)
      )
    })

    val failedCopies = copyResults.collect({case Failure(err)=>err})
    if(failedCopies.nonEmpty) {
      logger.error(s"${failedCopies.length} out of ${from.filePaths.length} copies failed: ")
      failedCopies.foreach(err=>logger.error("Copy failed: ", err))
      throw new RuntimeException("Could not copy all the files")
    } else {
      from
    }
  }

  def getOrCreateFileEntry(forFilePath:String, onStorageId:Int):Future[FileEntry] = {
    FileEntry.allVersionsFor(forFilePath,onStorageId).flatMap({
      case Success(results)=>
        results.headOption match {
          case Some(entry)=>Future(entry)
          case None=>
            val path = new File(forFilePath).toPath
            val modTime = Timestamp.from(Files.getLastModifiedTime(path).toInstant)
            val maybeCTimeRaw = Try { Files.getAttribute(path, "unix:ctime").asInstanceOf[java.nio.file.attribute.FileTime] }.toOption
            val maybeATimeRaw = Try { Files.getAttribute(path, "unix:atime").asInstanceOf[java.nio.file.attribute.FileTime] }.toOption

            val ctime = maybeCTimeRaw.map(ctime=>Timestamp.from(ctime.toInstant)).getOrElse(Timestamp.from(Instant.now))
            val atime = maybeATimeRaw.map(atime=>Timestamp.from(atime.toInstant)).getOrElse(Timestamp.from(Instant.now))

            val ent = FileEntry(None, forFilePath, onStorageId, "migration",1, ctime, modTime, atime, hasContent=true, hasLink=true)
            ent.save.map({
              case Success(savedEntry)=>savedEntry
              case Failure(exception)=>throw exception
            })
        }
      case Failure(err)=>
        logger.error(s"Could not get file entry for $forFilePath: ", err)
        Future.failed(err)
    })
  }

  /**
   * run a process to ingest project files for any projects that don't have them
   * @param projectSourcePath source path to look for legacy projects
   * @param destinationStorageId storage id to put them onto
   * @return a Future containing a 2-tuple - first is projects successfully copied, second is projects not found
   */
  def pullInProjectFiles(projectSourcePath:String, destinationStorageId:Int) =
    StorageEntryHelper.entryFor(destinationStorageId).flatMap({
      case Some(storageEntry)=>
        val multiCounterFac = new MultipleCounter[ProjectFileResult](2)

        val graph = GraphDSL.create(multiCounterFac){ implicit builder=> multiCounter=>
          import akka.stream.scaladsl.GraphDSL.Implicits._
          import slick.jdbc.PostgresProfile.api._

          val affectedProjects = builder.add(new ProjectNoFilesSource(dbConfigProvider, 20))
          val hasProjectsSwitch = builder.add(new ProjectFileExistsSwitch(projectSourcePath))
          val finalSink = builder.add(Sink.ignore)

          affectedProjects ~> hasProjectsSwitch

          //"YES" branch - projects exist
          hasProjectsSwitch.out(0)
            .map(entry=>copyFiles(entry, storageEntry.rootpath.get))  //also icky but it will work for the intended use
            .mapAsync(4)(entry=>{ //once copied create or find the file entries
              val fileEntryFutures = entry.filePaths.map(filepath=>getOrCreateFileEntry(filepath, destinationStorageId))
              val resultFuture = Future.sequence(fileEntryFutures)
              resultFuture.map(newFileEntries=>entry.copy(fileEntries=Some(newFileEntries)))
            })
            .mapAsync(4)(entry=>{ //now set up the associations
              val actions = DBIO.sequence(
                entry.fileEntries.get.map(fileEntry=>
                  lifted.TableQuery[FileAssociationRow]+=(entry.projectEntry.id.get,fileEntry.id.get)
                )
              )
              db.run(actions).map(_=>entry)
            }) ~> multiCounter.in(0)

          //"NO" branch - projects don't exist
          hasProjectsSwitch.out(1) ~> multiCounter.in(1)

          multiCounter.out ~> finalSink
          ClosedShape
        }

        RunnableGraph.fromGraph(graph).run().map(results=>(results.head, results(1)))

      case None=>
        Future.failed(new RuntimeException(s"No storage found with id $destinationStorageId"))
    })

}
