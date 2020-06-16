package models

import com.fasterxml.jackson.core.`type`.TypeReference
import play.api.libs.json.{Reads, Writes}
import slick.jdbc.PostgresProfile.api._

object ProductionOffice extends Enumeration {
  val UK,US,Aus = Value
}

/**
  * this object is intended to be imported into classes that use the ProductionOffice enum. It contains implicits
  * that allow Slick, Play JSON and Jackson-Databind (used by akka-persistence-jdbc) to use the enum
  */
object ProductionOfficeMapper {
  implicit val productionOfficeStringMapper = MappedColumnType.base[ProductionOffice.Value, String](
    e=>e.toString,
    s=>ProductionOffice.withName(s)
  )

  implicit val productionOfficeReads:Reads[ProductionOffice.Value] = Reads.enumNameReads(ProductionOffice)
  implicit val productionOfficeWrites:Writes[ProductionOffice.Value] = Writes.enumNameWrites
  class EnumStatusType extends TypeReference[EntryStatus.type] {}
}
