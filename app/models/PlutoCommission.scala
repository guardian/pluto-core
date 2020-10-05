package models

import java.sql.Timestamp
import java.time.ZonedDateTime

import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.api.libs.functional.syntax._
import slick.lifted.{TableQuery, Tag}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import ProductionOfficeMapper._
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

case class PlutoCommission (id:Option[Int], collectionId:Option[Int], siteId: Option[String], created: Timestamp, updated:Timestamp,
                            title: String, status: EntryStatus.Value, description: Option[String], workingGroup: Int,
                            originalCommissionerName:Option[String], scheduledCompletion:Timestamp, owner:String,
                            notes:Option[String],
                            @JsonScalaEnumeration(classOf[ProductionOfficeMapper.EnumStatusType]) productionOffice:ProductionOffice.Value,
                            originalTitle:Option[String]) extends PlutoModel {
  private def logger = Logger(getClass)

  var projectCount: Option[Int] = None
  /**
    *  writes this model into the database, inserting if id is None and returning a fresh object with id set. If an id
    * was set, then updates the database record and returns the same object. */
  def save(implicit db: slick.jdbc.JdbcProfile#Backend#Database):Future[Try[PlutoCommission]] = id match {
    case None=>
      logger.debug("Inserting commission record")
      val insertQuery = TableQuery[PlutoCommissionRow] returning TableQuery[PlutoCommissionRow].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=this).asTry
      ).map({
        case Success(insertResult)=>
          logger.debug(s"Successful insert: $insertResult")
          Success(insertResult.asInstanceOf[PlutoCommission])  //maybe only intellij needs the cast here?
        case Failure(error)=>
          logger.error(s"could not insert record: ",error)
          Failure(error)
      })
    case Some(realEntityId)=>
      logger.debug(s"Updating commission record $realEntityId")
      db.run(
        TableQuery[PlutoCommissionRow].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>Failure(error)
      })
  }

  /**
    * inserts this record into the database if there is nothing with the given uuid present
    * @param db - implicitly provided database object
    * @return a Future containing a Try containing a [[PlutoCommission]] object.
    *         If it was newly saved, or exists in the db, the id member will be set.
    */
  def ensureRecorded(implicit db: slick.jdbc.JdbcProfile#Backend#Database):Future[Try[PlutoCommission]] = {
    db.run(
      TableQuery[PlutoCommissionRow].filter(_.collectionId===collectionId).filter(_.siteId===siteId).result.asTry
    ).flatMap({
      case Success(rows)=>
        if(rows.isEmpty) {
          logger.info(s"Saving commission $title ($siteId-$collectionId) to the database")
          this.save
        } else {
          logger.info(s"Commission $title ($siteId-$collectionId) already existed")
          val updatedInfo = this.copy(id=Some(rows.head.id.get))
          updatedInfo.save
          Future(Success(updatedInfo))
        }
      case Failure(error)=>
        logger.error("could not check commission: ",error)
        Future(Failure(error))
    })
  }

  /**
    * returns the contents as a string->string map, for passing to postrun actions
    * @return
    */
  def asStringMap:Map[String,String] = Map(
    "commissionId"->s"$siteId-$collectionId",
    "commissionCreated"->created.toString,
    "commissionUpdated"->updated.toString,
    "commissionTitle"->title,
    "commissionDescription"->description.getOrElse("")
  )
}

class PlutoCommissionRow (tag:Tag) extends Table[PlutoCommission](tag,"PlutoCommission"){
  import EntryStatusMapper._
  import ProductionOfficeMapper._

  def id = column[Int]("id",O.PrimaryKey, O.AutoInc)
  def collectionId = column[Option[Int]]("i_collection_id")
  def siteId = column[Option[String]]("s_site_id")
  def created = column[Timestamp]("t_created")
  def updated = column[Timestamp]("t_updated")
  def title = column[String]("s_title")
  def status = column[EntryStatus.Value]("s_status")
  def description = column[Option[String]]("s_description")
  def workingGroup = column[Int]("k_working_group")
  def originalCommissionerName = column[Option[String]]("s_original_commissioner")
  def scheduledCompletion = column[Timestamp]("t_scheduled_completion")
  def owner = column[String]("s_owner")
  def notes = column[Option[String]]("s_notes")
  def productionOffice = column[ProductionOffice.Value]("s_production_office")
  def originalTitle = column[Option[String]]("s_original_title")
  
  def * = (id.?, collectionId, siteId, created, updated, title, status, description, workingGroup, originalCommissionerName, scheduledCompletion, owner, notes, productionOffice, originalTitle) <> (PlutoCommission.tupled, PlutoCommission.unapply)
}

trait PlutoCommissionSerializer extends TimestampSerialization {
  import EntryStatusMapper._

  implicit val plutoCommissionWrites: Writes[PlutoCommission] =
    (commission: PlutoCommission) => {
      val tuple = plutoCommissionTupleWrites.writes(commission).as[JsObject]
      commission.projectCount match {
        case Some(count) => tuple .+ ("projectCount", JsNumber(count))
        case None => tuple
      }
    }

  val plutoCommissionTupleWrites:Writes[PlutoCommission] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "collectionId").writeNullable[Int] and
      (JsPath \ "siteId").writeNullable[String] and
      (JsPath \ "created").write[Timestamp] and
      (JsPath \ "updated").write[Timestamp] and
      (JsPath \ "title").write[String] and
      (JsPath \ "status").write[EntryStatus.Value] and
      (JsPath \ "description").writeNullable[String] and
      (JsPath \ "workingGroupId").write[Int] and 
      (JsPath \ "originalCommissionerName").writeNullable[String] and
      (JsPath \ "scheduledCompletion").write[Timestamp] and
      (JsPath \ "owner").write[String] and
      (JsPath \ "notes").writeNullable[String] and
      (JsPath \ "productionOffice").write[ProductionOffice.Value] and
      (JsPath \ "originalTitle").writeNullable[String]
  )(unlift(PlutoCommission.unapply))

  implicit val plutoCommissionReads:Reads[PlutoCommission] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "collectionId").readNullable[Int] and
      (JsPath \ "siteId").readNullable[String] and
      (JsPath \ "created").read[Timestamp] and
      (JsPath \ "updated").read[Timestamp] and
      (JsPath \ "title").read[String] and
      (JsPath \ "status").read[EntryStatus.Value] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "workingGroupId").read[Int] and
      (JsPath \ "originalCommissionerName").readNullable[String] and
      (JsPath \ "scheduledCompletion").read[Timestamp] and
      (JsPath \ "owner").read[String] and
      (JsPath \ "notes").readNullable[String] and
      (JsPath \ "productionOffice").read[ProductionOffice.Value] and
      (JsPath \ "originalTitle").readNullable[String]
    )(PlutoCommission.apply _)
}

object PlutoCommission extends ((Option[Int],Option[Int],Option[String],Timestamp,Timestamp,String,EntryStatus.Value,Option[String],Int,Option[String],Timestamp,String,Option[String],ProductionOffice.Value, Option[String])=>PlutoCommission)  {
  def entryForVsid(vsid:String)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Option[PlutoCommission]] = {
    val idparts = vsid.split("-")
    if(idparts.length!=2) return Future(None)

    db.run(
      TableQuery[PlutoCommissionRow].filter(_.siteId===idparts.head).filter(_.collectionId===idparts(1).toInt).result
    ).map(_.headOption)
  }

  def forId(id:Int) (implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Option[PlutoCommission]] =
    db.run {
      TableQuery[PlutoCommissionRow].filter(_.id===id).result
    }.map(_.headOption)

  //handle different explicit time format
  def timestampToDateTime(t: Timestamp): DateTime = new DateTime(t.getTime)
  def dateTimeToTimestamp(dt: DateTime): Timestamp = new Timestamp(dt.getMillis)
  implicit val dateWrites = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSS") //this DOES take numeric timezones - Z means Zone, not literal letter Z
  implicit val dateReads = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSS")

  /**
    *  performs a conversion from java.sql.Timestamp to Joda DateTime and back again
    */
  implicit val timestampFormat = new Format[Timestamp] {
    def writes(t: Timestamp): JsValue = Json.toJson(timestampToDateTime(t))
    def reads(json: JsValue): JsResult[Timestamp] = Json.fromJson[DateTime](json).map(dateTimeToTimestamp)
  }
}