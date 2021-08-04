package models

import java.sql.Timestamp
import play.api.libs.json._

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

trait TimestampSerialization {
  def timestampToDateTime(t: Timestamp): ZonedDateTime = ZonedDateTime.ofInstant(t.toInstant, ZoneId.systemDefault())
  def dateTimeToTimestamp(dt: ZonedDateTime): Timestamp = new Timestamp(dt.toInstant.getEpochSecond*1000)

  /**
    *  performs a conversion from java.sql.Timestamp to Joda DateTime and back again
    */
  implicit val timestampFormat = new Format[Timestamp] {
    def writes(t: Timestamp): JsValue = Json.toJson(timestampToDateTime(t).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    def reads(json: JsValue): JsResult[Timestamp] = Json.fromJson[ZonedDateTime](json).map(dateTimeToTimestamp)
  }
}
