package services

import java.util.UUID

import akka.actor.Props
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.google.inject.Inject
import models.{EntryStatus, ProjectEntry, ProjectEntryRow}
import play.api.db.slick.DatabaseConfigProvider
import play.api.{Configuration, Logger}
import services.actors.MessageProcessorActor.{EventHandled, MessageEvent}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object CommissionStatusPropagator {
  def props = Props[CommissionStatusPropagator]

  trait CommissionStatusEvent {
    val uuid:UUID
  }
  case class CommissionStatusUpdate(commissionId:Int, newStatus:EntryStatus.Value, override val uuid:UUID) extends CommissionStatusEvent
  object CommissionStatusUpdate {
    def apply(commissionId:Int, newStatus:EntryStatus.Value) = new CommissionStatusUpdate(commissionId, newStatus, UUID.randomUUID())
  }
}

/**
 * this actor allows commission status changes to apply to their contained projects.
 *
 * the logic table is as follows:
 *   Commission “Completed” → all projects NOT Completed or Killed should be set to Completed
 *   Commission “Held” → all projects NOT Completed or Killed or Held should be set to Held
 *   Commission “In Progress” → no change
 *   Commission "Killed" -> all projects NOT Completed or Killed should be set to Killed
 * @param configuration
 * @param dbConfigProvider
 */
class CommissionStatusPropagator @Inject() (configuration:Configuration, dbConfigProvider:DatabaseConfigProvider) extends PersistentActor {
  import CommissionStatusPropagator._
  import models.EntryStatusMapper._

  override def persistenceId = "commission-status-propagator"

  private final var state:CommissionStatusPropagatorState = CommissionStatusPropagatorState(Map())
  private final var restoreCompleted = false
  val logger = Logger(getClass)

  protected val snapshotInterval = configuration.getOptional[Long]("pluto.persistence-snapshot-interval").getOrElse(50L)
  private implicit val db = dbConfigProvider.get[PostgresProfile].db

  /**
   * add an event to the journal, and snapshot if required
   * @param event event to add
   */
  def updateState(event:CommissionStatusEvent): Unit = {
    logger.debug(s"Marked event ${event.uuid} as pending")
    state = state.updated(event)
    if(lastSequenceNr % snapshotInterval ==0 && lastSequenceNr!=0)
      saveSnapshot(state)
  }

  /**
   * Logs to the journal that this event has been handled, so it won't be re-tried
   * @param evtAsObject event object
   */
  def confirmHandled(evtAsObject:  CommissionStatusEvent):Unit = {
    persist(EventHandled(evtAsObject.uuid)){ handledEventMarker=>
      logger.debug(s"marked event ${evtAsObject.uuid} as handled")
      state = state.removed(evtAsObject)
    }
  }

  override def receiveRecover: Receive = {
    case evt:CommissionStatusEvent=>
      updateState(evt)
    case handledEvt:EventHandled =>
      logger.debug(s"receiveRecover got message handled: ${handledEvt.eventId}")
      state = state.removed(handledEvt.eventId)
    case RecoveryCompleted=>
      logger.info(s"Completed journal recovery")
      restoreCompleted=true
    case SnapshotOffer(_, snapshot: CommissionStatusPropagatorState)=>
      logger.debug("receiveRecover got snapshot offer")
      state=snapshot
  }

  override def receiveCommand: Receive = {
    case evt@CommissionStatusUpdate(commissionId, newStatus, _)=>
      val originalSender = sender()

      persist(evt) { _=>
        logger.debug(s"Received notification that commission $commissionId changed to $newStatus")
        val maybeRequiredUpdate = newStatus match {
          case EntryStatus.Completed | EntryStatus.Killed=>
            val q = for { project <- TableQuery[ProjectEntryRow]
                              .filter(_.commission === commissionId)
                              .filter(_.status =!= EntryStatus.Completed)
                              .filter(_.status =!= EntryStatus.Killed)
                          } yield project.status
            Some(q.update(newStatus))
          case EntryStatus.Held=>
            val q = for { project <- TableQuery[ProjectEntryRow]
                              .filter(_.commission === commissionId)
                              .filter(_.status =!= EntryStatus.Completed)
                              .filter(_.status =!= EntryStatus.Killed)
                              .filter(_.status =!= EntryStatus.Held)
                          } yield project.status
            Some(q.update(EntryStatus.Held))
          case _=>None
        }

        maybeRequiredUpdate match {
          case Some(requiredUpdate) =>
            db.run(requiredUpdate).onComplete({
              case Failure(err) =>
                logger.error(s"Could not update project status for $commissionId to $newStatus: ", err)
                originalSender ! akka.actor.Status.Failure(err)
              case Success(updatedRecordCount) =>
                logger.info(s"Commission $commissionId status change to $newStatus updated the status of $updatedRecordCount contained projects")
                originalSender ! akka.actor.Status.Success(updatedRecordCount)
            })
          case None=>
            logger.info(s"Commission $commissionId status change to $newStatus did not need any project updates")
            originalSender ! akka.actor.Status.Success(0)
        }
      }
  }
}
