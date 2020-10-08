package models.datamigration

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax._

case class ProjectsUpdateRequest(vsBaseUri:String, vsUser:String, vsPasswd:String, vsSiteId:String, projectTypeId:Int)


object ProjectsUpdateRequest {
  implicit val projectUpdateReads:Reads[ProjectsUpdateRequest] = (
    (JsPath \ "vsBaseUri").read[String] and
      (JsPath \ "vsUser").read[String] and
      (JsPath \ "vsPasswd").read[String] and
      (JsPath \ "vsSiteId").read[String] and
      (JsPath \ "projectTypeId").read[Int]
    )(ProjectsUpdateRequest.apply _)
}
