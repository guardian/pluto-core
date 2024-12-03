package services

import java.util.UUID
import akka.actor.{Actor, ActorRef, Props}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.google.inject.Inject
import models.{EntryStatus, ProjectEntry, ProjectEntryRow, ProjectEntrySerializer}
import org.slf4j.LoggerFactory
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Writes
import services.RabbitMqPropagator._
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import javax.inject.Named
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
object CommissionStatusPropagator {
  private val logger = LoggerFactory.getLogger(getClass)
  def props = Props[CommissionStatusPropagator]

  trait CommissionStatusEvent {
    val uuid:UUID
  }

  class EnumStatusType extends TypeReference[EntryStatus.type] {}

  case class CommissionStatusUpdate(commissionId:Int, @JsonScalaEnumeration(classOf[EnumStatusType]) newValue:EntryStatus.Value, override val uuid:UUID) extends CommissionStatusEvent with JacksonSerializable
  case class RetryFromState(override val uuid: UUID) extends JacksonSerializable with CommissionStatusEvent

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
class CommissionStatusPropagator @Inject() (dbConfigProvider: DatabaseConfigProvider,@Named("rabbitmq-propagator") implicit val rabbitMqPropagator:ActorRef) extends Actor with models.ProjectEntrySerializer
  {
  import CommissionStatusPropagator._

  private final var state:CommissionStatusPropagatorState = CommissionStatusPropagatorState(Map())
  private final var restoreCompleted = false
    val logger = Logger(getClass)
    val dbConfig = dbConfigProvider.get[PostgresProfile]

    /**
   * Logs to the journal that this event has been handled, so it won't be re-tried
   * @param evtAsObject event object
   */
  def confirmHandled(evtAsObject:  CommissionStatusEvent):Unit = {
//    persist(EventHandled(evtAsObject.uuid)){ handledEventMarker=>
//      logger.debug(s"marked event ${evtAsObject.uuid} as handled")
//      state = state.removed(evtAsObject)
//    }
  }

    override def receive: Receive = {
    /**
      * re-run any messages stuck in the actor's state. This is sent at 5 minute intervals by ClockSingleton and
      * is there to ensure that events get retried (e.g. one instance loses network connectivity before postgres update is sent,
      * it is restarted, so another instance will pick up the update)
      */
    case RetryFromState =>
      if (state.size != 0) logger.warn(s"CommissionStatusPropagator retrying ${state.size} events from state")

      state.foreach { stateEntry =>
        logger.warn(s"Retrying event ${stateEntry._1}")
        self ! stateEntry._2
      }

    case evt@CommissionStatusUpdate(commissionId, newStatus, uuid) =>
      val originalSender = sender()

      logger.info(s"$uuid: Received notification that commission $commissionId changed to $newStatus")

      val futureResult: Future[Seq[Try[Int]]] = updateCommissionProjects(newStatus, commissionId)

      futureResult.onComplete {
        case Failure(err) =>
          logger.error(s"Could not fetch project entries for $commissionId to $newStatus: ", err)
          originalSender ! akka.actor.Status.Failure(err)
        case Success(updatedProjects) =>
          val successfulProjects = updatedProjects.collect { case Success(project) => project }
          logger.info(s"Project status change to $newStatus for ${successfulProjects.length} projects.")
          originalSender ! akka.actor.Status.Success(successfulProjects.length)

          if (successfulProjects.nonEmpty) {
            logger.info(s"Sending ${successfulProjects.length} updates to RabbitMQ.")
            }
          }
          confirmHandled(evt)
  }

    def updateCommissionProjects(newStatus: EntryStatus.Value, commissionId: Int): Future[Seq[Try[Int]]] = {
      // Wrap all operations in a single transaction
      val action = for {
        projectsWithIds <- ProjectEntry.getProjectsEligibleForStatusChange(newStatus, commissionId)
        _ = logger.info(s"Commission $commissionId status change to $newStatus: Found ${projectsWithIds.length} eligible projects: ${projectsWithIds.map(_._1).mkString(", ")}")
        updates <- DBIO.sequence(projectsWithIds.map { case (projectId, project) =>
          val updatedProject = project.copy(status = newStatus)
          TableQuery[ProjectEntryRow]
            .filter(_.id === projectId)
            .update(updatedProject)
            .map(result => (result, updatedProject))
        }).transactionally
      } yield projectsWithIds.zip(updates)

      dbConfig.db.run(action).flatMap { results =>
        // Handle RabbitMQ notifications after successful DB transaction
        val successfulUpdates = results.collect { 
          case ((projectId, _), (updateResult, _)) if updateResult > 0 => projectId 
        }
        logger.info(s"Commission $commissionId status change to $newStatus: Successfully updated ${successfulUpdates.length} projects: ${successfulUpdates.mkString(", ")}")
        
        results.map { case ((projectId, _), (updateResult, updatedProject)) =>
          if (updateResult > 0) {
            val projectSerializer = new ProjectEntrySerializer {}
            implicit val projectsWrites: Writes[ProjectEntry] = projectSerializer.projectEntryWrites
            rabbitMqPropagator ! ChangeEvent(Seq(projectsWrites.writes(updatedProject)), Some("project"), UpdateOperation())
            Future.successful(Success(projectId))
          } else {
            logger.error(s"Commission $commissionId status change to $newStatus: Failed to update project $projectId")
            Future.successful(Failure(new RuntimeException(s"Failed to update project $projectId")))
          }
        }
        Future.sequence(results.map(_._2))
      }.recover {
        case err =>
          logger.error(s"Failed to update projects for commission $commissionId to status $newStatus", err)
          Seq(Failure(err))
      }
    }
  }


