package models

import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.lifted.TableQuery
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class DeleteJobDAO(id: Option[Int], projectEntry:Int, status: String){
  val logger = Logger(getClass)
  def save(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[DeleteJobDAO]] = id match {
    case None=>
      val insertQuery = TableQuery[DeleteJob] returning TableQuery[DeleteJob].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
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
        TableQuery[DeleteJob].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>
          logger.error(s"Updating record failed due to: $error")
          Failure(error)
      })
  }
}

object DeleteJobDAO extends ((Option[Int], Int, String)=>DeleteJobDAO) {
  private val logger = Logger(getClass)

  def getOrCreate(projectId: Int, status: String)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[Try[DeleteJobDAO]] =
    db.run(
      TableQuery[DeleteJob].filter(_.projectEntry === projectId).result
    ).map(_.headOption).flatMap({
      case Some(entry) => Future(Success(entry))
        val updateJob = DeleteJobDAO(entry.id, projectId, status)
        updateJob.save
      case None =>
        val newJob = DeleteJobDAO(None, projectId, status)
        newJob.save
    })

  def getJobs(startAt:Int, limit:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) =
    db.run(
      TableQuery[DeleteJob].sortBy(_.id.asc).drop(startAt).take(limit).result.asTry
    )
}


class DeleteJob(tag: Tag) extends Table[DeleteJobDAO](tag, "DeleteJob") {
  def id=column[Int]("id",O.PrimaryKey,O.AutoInc)
  def projectEntry = column[Int]("k_project_entry")
  def status = column[String]("s_status")
  def * = (id.?, projectEntry, status) <> (DeleteJobDAO.tupled, DeleteJobDAO.unapply)
}

trait DeleteJobSerializer {
  implicit val defaultsWrites:Writes[DeleteJobDAO] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "projectEntry").write[Int] and
      (JsPath \ "status").write[String]
    )(unlift(DeleteJobDAO.unapply))

  implicit val defaultsReads:Reads[DeleteJobDAO] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "projectEntry").read[Int] and
      (JsPath \ "status").read[String]
    )(DeleteJobDAO.apply _)
}
