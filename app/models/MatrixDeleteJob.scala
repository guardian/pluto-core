package models

import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class MatrixDeleteJobDAO(id: Option[Int], projectEntry:Int, status: String){
  val logger = Logger(getClass)
  def save(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[MatrixDeleteJobDAO]] = id match {
    case None=>
      val insertQuery = TableQuery[MatrixDeleteJob] returning TableQuery[MatrixDeleteJob].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
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
        TableQuery[MatrixDeleteJob].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>
          logger.error(s"Updating record failed due to: $error")
          Failure(error)
      })
  }
}

object MatrixDeleteJobDAO extends ((Option[Int], Int, String)=>MatrixDeleteJobDAO) {
  private val logger = Logger(getClass)

  def getOrCreate(projectId: Int, status: String)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[Try[MatrixDeleteJobDAO]] =
    db.run(
      TableQuery[MatrixDeleteJob].filter(_.projectEntry === projectId).result
    ).map(_.headOption).flatMap({
      case Some(entry) => Future(Success(entry))
        val updateJob = MatrixDeleteJobDAO(entry.id, projectId, status)
        updateJob.save
      case None =>
        val newJob = MatrixDeleteJobDAO(None, projectId, status)
        newJob.save
    })
}


class MatrixDeleteJob(tag: Tag) extends Table[MatrixDeleteJobDAO](tag, "MatrixDeleteJob") {
  def id=column[Int]("id",O.PrimaryKey,O.AutoInc)
  def projectEntry = column[Int]("k_project_entry")
  def status = column[String]("s_status")
  def * = (id.?, projectEntry, status) <> (MatrixDeleteJobDAO.tupled, MatrixDeleteJobDAO.unapply)
}
