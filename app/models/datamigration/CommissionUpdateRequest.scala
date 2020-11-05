package models.datamigration

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class CommissionUpdateRequest(vsBaseUri:String, vsUser:String, vsPasswd:String, vsSiteId:String, defaultWorkingGroup:Option[Int])

object CommissionUpdateRequest {
  implicit val commissionUpdateReads:Reads[CommissionUpdateRequest] = (
    (JsPath \ "vsBaseUri").read[String] and
      (JsPath \ "vsUser").read[String] and
      (JsPath \ "vsPasswd").read[String] and
      (JsPath \ "vsSiteId").read[String] and
      (JsPath \ "defaultWorkingGroup").readNullable[Int]
  )(CommissionUpdateRequest.apply _)
}
