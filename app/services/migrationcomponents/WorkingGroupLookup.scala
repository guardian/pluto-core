package services.migrationcomponents

import models.PlutoWorkingGroup
import slick.jdbc.PostgresProfile

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class WorkingGroupLookup(name:String, entries:Seq[PlutoWorkingGroup]){

}

object WorkingGroupLookup {
  def load(from:VSGlobalMetadataGroup)(implicit db:PostgresProfile#Backend#Database) = {
    Future.sequence(from.entries.map(entry=>PlutoWorkingGroup.entryForUuid(entry.uuid)))
      .map(_.collect({case Some(entry)=>entry}))
      .map(workingGroups=>new WorkingGroupLookup(from.name, workingGroups))
  }
}