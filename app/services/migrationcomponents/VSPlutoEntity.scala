package services.migrationcomponents

import java.sql.Timestamp
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import play.api.libs.json.{JsArray, JsValue, __}

/**
  * this trait contains the basic data extraction primitives for VSProjectentity and VSCommissionEntity
  */
trait VSPlutoEntity {
  final val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

  protected val rawData:JsValue

  def dump = {
    rawData.toString()
  }

  /**
    * returns a (possibly empty) sequence of strings for each available value of the given field
    * @param key fieldname to look for
    * @return
    */
  def getMeta(key:String) = {
    for {
      timespan <- (rawData \ "metadata" \ "timespan").as[JsArray].value
      fieldBlock <- (timespan \ "field").as[JsArray].value
      valueBlock <- (fieldBlock \ "value").as[JsArray].value if (fieldBlock \ "name").as[String] == key
    } yield (valueBlock \ "value").as[String]
  }


  /**
    * returns an option with None if no fields match or a sequence of values if they do
    * @param key fieldname to look for
    * @return
    */
  def getMetaOptional(key:String) = {
    val s = getMeta(key)
    if(s.isEmpty) {
      None
    } else {
      Some(s)
    }
  }

  def getSingle(key:String) = getMetaOptional(key).flatMap(_.headOption)

  def created = {
    getSingle("created")
      .map(stringVal=>ZonedDateTime.parse(stringVal, dateFormatter))
      .map(_.toLocalDateTime)
      .map(Timestamp.valueOf)
  }

  def updated = {
    getSingle("__metadata_last_modified")
      .map(stringVal=>ZonedDateTime.parse(stringVal, dateFormatter))
      .map(_.toLocalDateTime)
      .map(Timestamp.valueOf)
  }
}
