package services.migrationcomponents

import java.sql.Timestamp
import java.time.{LocalDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter

import play.api.libs.json._

import scala.util.{Success, Try}

class VSProjectEntity (override protected val rawData:JsValue) extends VSPlutoEntity {
  def title = {
    Seq(
      getSingle("gnm_project_headline"),
      getSingle("title")
    ).collectFirst({ case Some(value)=>value })
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

  def fromList(listEntries:JsValue):IndexedSeq[VSProjectEntity] = listEntries.as[JsArray].value.map(apply).toIndexedSeq
}