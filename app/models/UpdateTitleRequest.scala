package models

import java.sql.Timestamp

import play.api.libs.json.{JsPath, Reads, Writes}
import play.api.libs.functional.syntax._

case class UpdateTitleRequest (newTitle:String, updated: Timestamp, newVsid:Option[String])

trait UpdateTitleRequestSerializer extends TimestampSerialization {
  implicit val updateTitleRequestReads:Reads[UpdateTitleRequest] = (
    (JsPath \ "title").read[String] and
      (JsPath \ "updated").read[Timestamp] and
      (JsPath \ "vsid").readNullable[String]
    )(UpdateTitleRequest.apply _)

}
