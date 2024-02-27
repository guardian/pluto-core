package models

import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.lifted.TableQuery
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import java.sql.Timestamp

case class DeletionRecordDAO(id: Option[Int], projectEntry:Int, user:String, deleted:Timestamp, created:Timestamp, workingGroup:String){
  val logger = Logger(getClass)
  def save(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[DeletionRecordDAO]] = id match {
    case None=>
      val insertQuery = TableQuery[DeletionRecord] returning TableQuery[DeletionRecord].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=this).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult)
        case Failure(error)=>
          logger.error(s"Inserting record failed due to: $error")
          Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[DeletionRecord].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>
          logger.error(s"Updating record failed due to: $error")
          Failure(error)
      })
  }
}

object DeletionRecordDAO extends ((Option[Int], Int, String, Timestamp, Timestamp, String)=>DeletionRecordDAO) {
  private val logger = Logger(getClass)

  def getOrCreate(projectId: Int, user: String, deleted: Timestamp, created: Timestamp, workingGroup: String)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[Try[DeletionRecordDAO]] =
    db.run(
      TableQuery[DeletionRecord].filter(_.projectEntry === projectId).result
    ).map(_.headOption).flatMap({
      case Some(entry) => Future(Success(entry))
        val updateRecord = DeletionRecordDAO(entry.id, projectId, user, deleted, created, workingGroup)
        updateRecord.save
      case None =>
        val newRecord = DeletionRecordDAO(None, projectId, user, deleted, created, workingGroup)
        newRecord.save
    })

  def getRecords(startAt:Int, limit:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) =
    db.run(
      TableQuery[DeletionRecord].sortBy(_.id.asc).drop(startAt).take(limit).result.asTry
    )

  def getRecord(requestedId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    db.run(
      TableQuery[DeletionRecord].filter(_.id===requestedId).result.asTry
    ).map(_.map(_.head))
  }
}


class DeletionRecord(tag: Tag) extends Table[DeletionRecordDAO](tag, "DeletionRecord") {
  def id=column[Int]("id",O.PrimaryKey,O.AutoInc)
  def projectEntry = column[Int]("k_project_entry")
  def user=column[String]("s_user")
  def deleted=column[Timestamp]("t_deleted")
  def created=column[Timestamp]("t_created")
  def workingGroup=column[String]("s_working_group")
  def * = (id.?, projectEntry, user, deleted, created, workingGroup) <> (DeletionRecordDAO.tupled, DeletionRecordDAO.unapply)
}

trait DeletionRecordSerializer extends TimestampSerialization {
  implicit val defaultsWrites:Writes[DeletionRecordDAO] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "projectEntry").write[Int] and
      (JsPath \ "user").write[String] and
      (JsPath \ "deleted").write[Timestamp] and
      (JsPath \ "created").write[Timestamp] and
      (JsPath \ "workingGroup").write[String]
    )(unlift(DeletionRecordDAO.unapply))

  implicit val defaultsReads:Reads[DeletionRecordDAO] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "projectEntry").read[Int] and
      (JsPath \ "user").read[String] and
      (JsPath \ "deleted").read[Timestamp] and
      (JsPath \ "created").read[Timestamp] and
      (JsPath \ "workingGroup").read[String]
    )(DeletionRecordDAO.apply _)
}
