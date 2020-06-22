package models

import play.api.libs.json.{Reads, Writes}
import slick.jdbc.PostgresProfile.api._

object EntryStatus extends Enumeration {
  val New,Held,Completed,Killed = Value
  val InProduction = Value("In Production")
}

object EntryStatusMapper {
  implicit val commissionStatusStringMapper = MappedColumnType.base[EntryStatus.Value, String](
    e=>e.toString,
    s=>EntryStatus.withName(s)
  )
  implicit val entryStatusReads:Reads[EntryStatus.Value] = Reads.enumNameReads(EntryStatus)
  implicit val entryStatusWrites:Writes[EntryStatus.Value] = Writes.enumNameWrites
}