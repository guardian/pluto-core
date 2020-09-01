package models

import java.sql.Timestamp
import java.time.{Instant}

import play.api.libs.json._

trait TimestampSerialization {
  def timestampToInstant(t: Timestamp): Instant = t.toInstant
  def instantToTimestamp(dt: Instant): Timestamp = Timestamp.from(dt)

  /**
    *  performs a conversion from java.sql.Timestamp to Instant and back again
    */
  implicit val timestampFormat = new Format[Timestamp] {
    def writes(t: Timestamp): JsValue = Json.toJson(timestampToInstant(t))
    def reads(json: JsValue): JsResult[Timestamp] = Json.fromJson[Instant](json).map(instantToTimestamp)
  }
}
