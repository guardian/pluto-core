package models

import play.api.libs.json.{JsPath, Writes}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import java.util.UUID
import slick.jdbc.PostgresProfile.api._
import play.api.libs.json.DefaultWrites
import java.sql.Timestamp

object ValidationJobStatus extends Enumeration {
  val Pending, Running, Success, Failure = Value
}

object ValidationJobType extends Enumeration {
  val CheckAllFiles, CheckSomeFiles, MislinkedPTR, UnlinkedProjects, UnlinkedFiles = Value
}

case class ValidationJob(id:Option[Int],uuid:UUID,
                         userName:String,
                         jobType:ValidationJobType.Value,
                         startedAt:Option[Timestamp],
                         completedAt:Option[Timestamp],
                         status:ValidationJobStatus.Value,
                         errorMessage:Option[String])

object ValidationJob extends ( (Option[Int], UUID, String, ValidationJobType.Value, Option[Timestamp], Option[Timestamp], ValidationJobStatus.Value, Option[String])=>ValidationJob) {
  def apply(jobType:ValidationJobType.Value, userName:String):ValidationJob = {
    new ValidationJob(None, UUID.randomUUID(), userName, jobType, None, None, ValidationJobStatus.Pending, None)
  }
}

object ValidationJobMappers {
  implicit val jobStatusMapper = MappedColumnType.base[ValidationJobStatus.Value, String](
    e => e.toString,
    s => ValidationJobStatus.withName(s)
  )

  implicit val jobTypeMapper = MappedColumnType.base[ValidationJobType.Value, String](
    j => j.toString,
    s => ValidationJobType.withName(s)
  )

  implicit val validationJobStatusReads:Reads[ValidationJobStatus.Value] = Reads.enumNameReads(ValidationJobStatus)
  implicit val validationJobStatusWrites:Writes[ValidationJobStatus.Value] = Writes.enumNameWrites
  implicit val validationJobTypeReads:Reads[ValidationJobType.Value] = Reads.enumNameReads(ValidationJobType)
  implicit val validationJobTypeWrites:Writes[ValidationJobType.Value] = Writes.enumNameWrites
}

class ValidationJobRow(tag:Tag) extends Table[ValidationJob](tag, "ValidationJob") {
  import ValidationJobMappers._

  def id = column[Int]("id",O.PrimaryKey,O.AutoInc)
  def uuid = column[UUID]("u_uuid", O.Unique)
  def userName = column[String]("s_username")
  def jobType =column[ValidationJobType.Value]("s_jobtype")
  def startedAt = column[Option[Timestamp]]("t_started_at")
  def completedAt = column[Option[Timestamp]]("t_completed_at")
  def status = column[ValidationJobStatus.Value]("s_status")
  def errorMessage = column[Option[String]]("s_error_message")

  def uuidIndex = index("validationjob_uuid", uuid)
  def statusIndex = index("validationjob_status", status)

  def * = (id.?, uuid, userName, jobType, startedAt, completedAt, status, errorMessage) <> (ValidationJob.tupled, ValidationJob.unapply)
}

trait ValidationJobSerializer extends TimestampSerialization with DefaultWrites {
  import ValidationJobMappers._

  implicit val validationJobWrites:Writes[ValidationJob] = (
    (JsPath \ "pk").writeNullable[Int] and
    (JsPath \ "uuid").write[UUID] and
    (JsPath \ "userName").write[String] and
    (JsPath \ "jobType").write[ValidationJobType.Value] and
    (JsPath \ "startedAt").writeNullable[Timestamp] and
    (JsPath \ "completedAt").writeNullable[Timestamp] and
    (JsPath \ "status").write[ValidationJobStatus.Value] and
    (JsPath \ "errorMessage").writeNullable[String]
  )(unlift(ValidationJob.unapply))

}