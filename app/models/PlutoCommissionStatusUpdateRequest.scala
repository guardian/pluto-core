package models

import java.sql.Timestamp

import play.api.libs.json.Reads
import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class PlutoCommissionStatusUpdateRequest(status:EntryStatus.Value, updated: Timestamp, dummy:Option[String])

object PlutoCommissionStatusUpdateRequestSerializer extends TimestampSerialization {
  import EntryStatusMapper._
  implicit val reads:Reads[PlutoCommissionStatusUpdateRequest] = (
    (JsPath \ "status").read[EntryStatus.Value] and
      (JsPath \ "updated").read[Timestamp] and
      (JsPath \ "dummy").readNullable[String]
  )(PlutoCommissionStatusUpdateRequest.apply _)
}