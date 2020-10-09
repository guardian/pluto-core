package services.migrationcomponents

import java.util.UUID

import models.{PlutoCommission, PlutoWorkingGroup}

import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

object ProjectHelper {
  def findWorkingGroup(vsProject: VSProjectEntity)(implicit db: slick.jdbc.PostgresProfile#Backend#Database) =
    vsProject.getSingle("gnm_commission_workinggroup") match {
      case Some(workingGroupUuid) =>
        Future.fromTry(Try {
          UUID.fromString(workingGroupUuid)
        }).flatMap(PlutoWorkingGroup.entryForUuid)
      case None =>
        Future(None)
    }

  def findCommission(vsProject:VSProjectEntity)(implicit db: slick.jdbc.PostgresProfile#Backend#Database) =
    vsProject.getSingle("__collection") match {
      case Some(vsid) =>
        PlutoCommission.entryForVsid(vsid)
      case None=>
        Future(None)
    }
}
