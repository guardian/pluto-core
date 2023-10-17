package models

import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.lifted.TableQuery
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class MatrixDeleteDataDAO(id: Option[Int], projectEntry:Int, item: String) extends PlutoModel {
  val logger = Logger(getClass)
  def save(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[MatrixDeleteDataDAO]] = id match {
    case None=>
      val insertQuery = TableQuery[MatrixDeleteData] returning TableQuery[MatrixDeleteData].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
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
        TableQuery[MatrixDeleteData].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>
          logger.error(s"Updating record failed due to: $error")
          Failure(error)
      })
  }
}

object MatrixDeleteDataDAO extends ((Option[Int], Int, String)=>MatrixDeleteDataDAO) {
  private val logger = Logger(getClass)

  def getOrCreate(projectId: Int, item: String)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[Try[MatrixDeleteDataDAO]] =
    db.run(
      TableQuery[MatrixDeleteData].filter(_.projectEntry === projectId).filter(_.item === item).result
    ).map(_.headOption).flatMap({
      case Some(entry) => Future(Success(entry))
        val updateJob = MatrixDeleteDataDAO(entry.id, projectId, item)
        updateJob.save
      case None =>
        val newJob = MatrixDeleteDataDAO(None, projectId, item)
        newJob.save
    })

  def itemsForProject(projectId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) =
    db.run(
      TableQuery[MatrixDeleteData].filter(_.projectEntry === projectId).sortBy(_.item.asc).result.asTry
    )
}


class MatrixDeleteData(tag: Tag) extends Table[MatrixDeleteDataDAO](tag, "MatrixDeleteData") {
  def id=column[Int]("id",O.PrimaryKey,O.AutoInc)
  def projectEntry = column[Int]("k_project_entry")
  def item = column[String]("s_item")
  def * = (id.?, projectEntry, item) <> (MatrixDeleteDataDAO.tupled, MatrixDeleteDataDAO.unapply)
}

trait MatrixDeleteDataSerializer {
  implicit val defaultsWrites:Writes[MatrixDeleteDataDAO] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "projectEntry").write[Int] and
      (JsPath \ "item").write[String]
    )(unlift(MatrixDeleteDataDAO.unapply))

  implicit val defaultsReads:Reads[MatrixDeleteDataDAO] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "projectEntry").read[Int] and
      (JsPath \ "item").read[String]
    )(MatrixDeleteDataDAO.apply _)
}