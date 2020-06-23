package models

import play.api.libs.json.Reads
import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class PlutoCommissionStatusUpdateRequest(status:EntryStatus.Value, dummy:Option[String])

object PlutoCommissionStatusUpdateRequestSerializer {
  import EntryStatusMapper._
  implicit val reads:Reads[PlutoCommissionStatusUpdateRequest] = (
    (JsPath \ "status").read[EntryStatus.Value] and
      (JsPath \ "dummy").readNullable[String]
  )(PlutoCommissionStatusUpdateRequest.apply _)
}