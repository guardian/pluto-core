package services

import java.util.UUID

import akka.actor.Props
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.google.inject.Inject
import models.{EntryStatus, ProjectEntry, ProjectEntryRow}
import play.api.db.slick.DatabaseConfigProvider
import play.api.{Configuration, Logger}
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

  class EnumStatusType extends TypeReference[EntryStatus.type] {}

  case class CommissionStatusUpdate(commissionId:Int, @JsonScalaEnumeration(classOf[EnumStatusType]) newValue:EntryStatus.Value, override val uuid:UUID) extends CommissionStatusEvent with JacksonSerializable
  case object RetryFromState extends JacksonSerializable

  object CommissionStatusUpdate {
    def apply(commissionId:Int, newStatus:EntryStatus.Value) = new CommissionStatusUpdate(commissionId, newStatus, UUID.randomUUID())
  }

  case class EventHandled(uuid: UUID) extends CommissionStatusEvent
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
      logger.debug(s"receiveRecover got message handled: ${handledEvt.uuid}")
      state = state.removed(handledEvt.uuid)
    case RecoveryCompleted=>
      logger.info(s"Completed journal recovery, kicking off pending items")
      restoreCompleted=true
      state.foreach { stateEntry =>
        logger.debug(s"${stateEntry._1.toString}: ${stateEntry._2.toString}")
        self ! stateEntry._2
      }
    case SnapshotOffer(_, snapshot: CommissionStatusPropagatorState)=>
      logger.debug("receiveRecover got snapshot offer")
      state=snapshot
  }

  override def receiveCommand: Receive = {
    /**
      * re-run any messages stuck in the actor's state. This is sent at 5 minute intervals by ClockSingleton and
      * is there to ensure that events get retried (e.g. one instance loses network connectivity before postgres update is sent,
      * it is restarted, so another instance will pick up the update)
      */
    case RetryFromState=>
      if(state.size!=0) logger.warn(s"CommissionStatusPropagator retrying ${state.size} events from state")

      state.foreach { stateEntry=>
        logger.warn(s"Retrying event ${stateEntry._1}")
        self ! stateEntry._2
      }

    case evt@CommissionStatusUpdate(commissionId, newStatus, uuid)=>
      val originalSender = sender()

      persist(evt) { _=>
        logger.info(s"${uuid}: Received notification that commission $commissionId changed to $newStatus")
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
                originalSender ! akka.actor.Status.Failure(err) //leave it open for retries
              case Success(updatedRecordCount) =>
                logger.info(s"Commission $commissionId status change to $newStatus updated the status of $updatedRecordCount contained projects")
                originalSender ! akka.actor.Status.Success(updatedRecordCount)
                confirmHandled(evt)
            })
          case None=>
            logger.info(s"Commission $commissionId status change to $newStatus did not need any project updates")
            originalSender ! akka.actor.Status.Success(0)
            confirmHandled(evt)
        }
      }
  }
}
