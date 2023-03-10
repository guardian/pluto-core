package services.migrationcomponents

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.util.UUID

import models.{EntryStatus, ProductionOffice}
import play.api.libs.json.{JsArray, JsValue}
import java.time.Instant
import scala.util.Try

case class VSCommissionEntity(override protected val rawData:JsValue) extends VSPlutoEntity {
  def collectionId = getSingle("collectionId")

  def title = getSingle("gnm_commission_title")

  def childCollections = getMeta("__child_collection")

  def status = getSingle("gnm_commission_status").map(entry=>{
    if(entry=="In production") {  //sometimes we have non-compliant values in VS that need to be dealt with
      "In Production"
    } else if(entry=="Complete") {
      "Completed"
    } else {
      entry
    }

  }).map(EntryStatus.withName)

  def description = getMetaOptional("gnm_commission_description").map(_.mkString("\n"))

  def commissionerId = getSingle("gnm_commission_commissioner").flatMap(stringVal=>Try {UUID.fromString(stringVal) }.toOption)

  def workingGroupId = getSingle("gnm_commission_workinggroup").flatMap(stringVal=>Try {UUID.fromString(stringVal) }.toOption)

  def notes = getMetaOptional("gnm_commission_notes")
    .map(_.mkString("\n"))
    .flatMap(value=>{
      if(value=="") {
        None
      } else {
        Some(value)
      }
    })

  def ownerId = getMetaOptional("gnm_commission_owner")

  def productionOffice = getSingle("gnm_commission_production_office").map(ProductionOffice.withName)

  def scheduledCompletion = getSingle("gnm_commission_scheduled_completion").map(dateString=>{
    val formatter = DateTimeFormatter.ISO_DATE
    val parsedDate = formatter.parse(dateString)
    Timestamp.from(Instant.from(parsedDate))
  })
}

object VSCommissionEntity {
  def apply(rawData:JsValue) = new VSCommissionEntity(rawData)

  def fromList(entries:JsValue):IndexedSeq[VSCommissionEntity] = entries.as[JsArray].value.map(apply).toIndexedSeq
}
