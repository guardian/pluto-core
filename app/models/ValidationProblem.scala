package models

import play.api.libs.json.{JsPath, Writes}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import java.util.UUID
import slick.jdbc.PostgresProfile.api._
import play.api.libs.json.DefaultWrites

import java.sql.Timestamp
import java.time.Instant

object ValidationEntityClass extends Enumeration {
  val ProjectEntry = Value
}

case class ValidationProblem(pk:Option[Int], jobId:UUID, timestamp:Timestamp, entityClass:ValidationEntityClass.Value, entityId:Int, notes:Option[String])

object ValidationProblem extends ((Option[Int], UUID, Timestamp, ValidationEntityClass.Value, Int, Option[String])=>ValidationProblem) {
  /**
    * build an empty ValidationProblem record associated with a given job
    * @param job
    * @param entityClass
    * @param entityId
    * @param timestamp
    * @param notes
    * @return
    */
  def forJob(job:ValidationJob, entityClass:ValidationEntityClass.Value, entityId:Int, timestamp:Timestamp=Timestamp.from(Instant.now()), notes:Option[String]=None) = {
    new ValidationProblem(None, job.uuid, timestamp, entityClass, entityId, notes)
  }

  /**
    * build a ValidationProblem record associated with the given ProjectEntry and ValidationJob
    * @param projectEntry project that has a problem
    * @param job the job that is detecting this issue
    * @param notes any extra notes (Optional, defaults to None)
    * @param timestamp the current time (Optional, defaults to 'Now')
    * @return the ValidationProblem record, or None if the project entry is not valid
    */
  def fromProjectEntry(projectEntry: ProjectEntry, job:ValidationJob, notes:Option[String]=None, timestamp: Option[Timestamp]=None) = {
    projectEntry.id.map(projectId=>
      new ValidationProblem(
        None, job.uuid, timestamp.getOrElse(Timestamp.from(Instant.now())), ValidationEntityClass.ProjectEntry, projectId, notes
      )
    )
  }
}

object ValidationProblemMappers {
  implicit val validationEntityMapper = MappedColumnType.base[ValidationEntityClass.Value, String](
    e => e.toString,
    s => ValidationEntityClass.withName(s)
  )
}

class ValidationProblemRow(tag:Tag) extends Table[ValidationProblem](tag, "ValidationProblem") {
  import ValidationProblemMappers._

  def pk = column[Int]("pk",O.PrimaryKey, O.AutoInc)
  def jobId = column[UUID]("u_job_id")
  def timestamp = column[Timestamp]("t_timestamp")
  def entityClass = column[ValidationEntityClass.Value]("s_entity_class")
  def entityId = column[Int]("i_entity_id")
  def notes = column[Option[String]]("s_notes")

  def jobIdFK = foreignKey("fk_job_id", jobId,TableQuery[ValidationJobRow])(_.uuid)

  def * = (pk.?, jobId, timestamp, entityClass, entityId, notes) <> (ValidationProblem.tupled, ValidationProblem.unapply)
}

