package services.migrationcomponents

import java.util.UUID

import models.{PlutoCommission, PlutoWorkingGroup}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

object ProjectHelper {
  private val logger = LoggerFactory.getLogger(getClass)
  def findWorkingGroup(vsProject: VSProjectEntity)(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[Option[PlutoWorkingGroup]] =
    vsProject.getSingle("gnm_commission_workinggroup") match {
      case Some(workingGroupUuid) =>
        Try {
          UUID.fromString(workingGroupUuid)
        } match {
          case Failure(err)=>
            logger.warn(s"Invalid UUID on ${vsProject.title}: ${err.toString}")
            Future(None)
          case Success(uuid)=>
            PlutoWorkingGroup.entryForUuid(uuid)
        }
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
