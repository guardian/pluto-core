package models.datamigration

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class CommissionUpdateRequest(vsBaseUri:String, vsUser:String, vsPasswd:String, vsSiteId:String)

object CommissionUpdateRequest {
  implicit val commissionUpdateReads:Reads[CommissionUpdateRequest] = (
    (JsPath \ "vsBaseUri").read[String] and
      (JsPath \ "vsUser").read[String] and
      (JsPath \ "vsPasswd").read[String] and
      (JsPath \ "vsSiteId").read[String]
  )(CommissionUpdateRequest.apply _)
}
