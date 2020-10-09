package models.datamigration

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax._

case class ProjectRelinkRequest(sourceFilePath:String, destinationStorageId:Int)

object ProjectRelinkRequest {
  implicit val projectRelinkReads:Reads[ProjectRelinkRequest] = (
    (JsPath \ "sourceFilePath").read[String] and
      (JsPath \ "destinationStorageId").read[Int]
  )(ProjectRelinkRequest.apply _)
}
