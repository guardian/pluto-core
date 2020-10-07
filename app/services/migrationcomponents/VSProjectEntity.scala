package services.migrationcomponents

import java.sql.Timestamp
import java.time.{LocalDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter

import play.api.libs.json._

import scala.util.{Success, Try}

class VSProjectEntity (private val rawData:JsValue) {
  private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME
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

  def title = {
    Seq(
      getSingle("gnm_project_headline"),
      getSingle("title")
    ).collectFirst({ case Some(value)=>value })
  }

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

  def owner_id_list = {
    getMeta("gnm_project_username").map(numberStr=>Try { numberStr.toInt }).collect({case Success(n)=>n})
  }

  def boolFromAnyString(key:String) =
    getMeta(key).filter(str=>str!="").nonEmpty

  def isDeletable = boolFromAnyString("gnm_storage_rule_deletable")

  def isSensitive = boolFromAnyString("gnm_storage_rule_sensitive")

  def isDeepArchive = boolFromAnyString("gnm_storage_rule_deep_archive")
}

object VSProjectEntity {
  def apply(rawData:JsValue) = new VSProjectEntity(rawData)

  def fromList(listEntries:JsValue):scala.collection.IndexedSeq[VSProjectEntity] = listEntries.as[JsArray].value.map(apply)
}