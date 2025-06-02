package models

import akka.stream.scaladsl.Source
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import play.api.Logger
import java.sql.Timestamp
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class StatusChangeDAO (id: Option[Int], projectId: Int, time:Timestamp, user: String, status: String, title: String){
  val logger = Logger(getClass)
  def save(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[StatusChangeDAO]] = id match {
    case None=>
      val insertQuery = TableQuery[StatusChange] returning TableQuery[StatusChange].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=this).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult)
        case Failure(error)=>
          logger.error(s"Inserting change failed due to: $error")
          Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[StatusChange].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>
          logger.error(s"Updating change failed due to: $error")
          Failure(error)
      })
  }
}

object StatusChangeDAO extends ((Option[Int], Int, Timestamp, String, String, String)=>StatusChangeDAO) {

  def create (projectId: Int, time: Timestamp, user: String, status: String, title: String)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[Try[StatusChangeDAO]] =
    db.run(
      TableQuery[StatusChange].filter(_.id === 0).result
    ).map(_.headOption).flatMap({
      case None =>
        val newRecord = StatusChangeDAO(None, projectId, time, user, status, title)
        newRecord.save
    })

  def entryForId(requestedId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[StatusChangeDAO]] = {
    db.run(
      TableQuery[StatusChange].filter(_.id===requestedId).result.asTry
    ).map(_.map(_.head))
  }

  def entryForIdNew(requestedId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[StatusChangeDAO] =
    db.run(
      TableQuery[StatusChange].filter(_.id===requestedId).result
    ).map(_.head)

  def scanAllChanges(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    Source.fromPublisher(db.stream(TableQuery[StatusChange].sortBy(_.time.desc).result))
  }

  def getRecords(startAt:Int, limit:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) =
    db.run(
      TableQuery[StatusChange].sortBy(_.id.desc).drop(startAt).take(limit).result.asTry
    )
}

class StatusChange(tag:Tag) extends Table[StatusChangeDAO](tag, "StatusChange") {

  implicit val DateTimeTotimestamp =
    MappedColumnType.base[DateTime, Timestamp]({d=>new Timestamp(d.getMillis)}, {t=>new DateTime(t.getTime, UTC)})

  def id=column[Int]("id",O.PrimaryKey,O.AutoInc)
  def projectId=column[Int]("k_project_id")
  def time=column[Timestamp]("t_time")
  def user=column[String]("s_user")
  def status = column[String]("s_status")
  def title = column[String]("s_title")

  def * = (id.?, projectId, time, user, status, title) <> (StatusChangeDAO.tupled, StatusChangeDAO.unapply)

}

trait StatusChangeSerializer extends TimestampSerialization {

  implicit val statusChangeWrites:Writes[StatusChangeDAO] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "projectId").write[Int] and
      (JsPath \ "time").write[Timestamp] and
      (JsPath \ "user").write[String] and
      (JsPath \ "status").write[String] and
      (JsPath \ "title").write[String]
    )(unlift(StatusChangeDAO.unapply))

  implicit val statusChangeReads:Reads[StatusChangeDAO] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "projectId").read[Int] and
      (JsPath \ "time").read[Timestamp] and
      (JsPath \ "user").read[String] and
      (JsPath \ "status").read[String] and
      (JsPath \ "title").read[String]
    )(StatusChangeDAO.apply _)
}


