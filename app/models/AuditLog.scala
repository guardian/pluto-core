package models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.lifted.{TableQuery, Tag}
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import java.time.ZonedDateTime

object AuditAction extends Enumeration {
  type AuditAction = Value
  val OpenProject, ViewProjectPage, CreateProject, ChangeProjectStatus = Value
}

case class AuditLog (id:Option[Int], username:String, actionType:AuditAction.AuditAction, targetObjectId:Int, at:Timestamp, data:Option[String]) extends PlutoModel

class AuditLogRow (tag:Tag) extends Table[AuditLog](tag, "AuditLog") with AuditLogSerializer {
  def id = column[Int]("id",O.PrimaryKey, O.AutoInc)
  def username = column[String]("s_username")
  def actionType = column[AuditAction.Value]("s_type")
  def targetObjectId = column[Int]("i_target_object")
  def at = column[Timestamp]("t_at")
  def data = column[Option[String]]("s_data")

  def * = (id.?, username, actionType, targetObjectId, at, data) <> (AuditLog.tupled, AuditLog.unapply)
}

trait AuditLogSerializer extends TimestampSerialization {
  implicit val auditActionReads:Reads[AuditAction.Value] = Reads.enumNameReads(AuditAction)
  implicit val auditActionWrites:Writes[AuditAction.Value] = Writes.enumNameWrites

  implicit val auditActionMapper: JdbcType[AuditAction.Value] with BaseTypedType[AuditAction.Value] = MappedColumnType.base[AuditAction.Value, String](
    e=>e.toString,
    s=>AuditAction.withName(s)
  )

  implicit val AuditLogWrites:Writes[AuditLog] = (
    (JsPath \ "id").writeNullable[Int] and
    (JsPath \ "username").write[String] and
    (JsPath \ "actionType").write[AuditAction.Value] and
    (JsPath \ "targetObjectId").write[Int] and
    (JsPath \ "at").write[Timestamp] and
    (JsPath \ "data").writeNullable[String]
  )(unlift(AuditLog.unapply))

  implicit val AuditLogReads:Reads[AuditLog] = (
    (JsPath \ "id").readNullable[Int] and
    (JsPath \ "username").read[String] and
    (JsPath \ "actionType").read[AuditAction.Value] and
    (JsPath \ "targetObjectId").read[Int] and
    (JsPath \ "at").read[Timestamp] and
    (JsPath \ "data").readNullable[String]
  )(AuditLog.apply _)

}