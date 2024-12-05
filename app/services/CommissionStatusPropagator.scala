package services

import java.util.UUID
import akka.actor.{Actor, ActorRef, Props}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.google.inject.Inject
import models.{EntryStatus, ProjectEntry, ProjectEntryRow, ProjectEntrySerializer, EntryStatusMapper}
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
      }
      confirmHandled(evt)
  }

    def updateCommissionProjects(newStatus: EntryStatus.Value, commissionId: Int, projectsToVerify: Set[Int] = Set.empty): Future[Seq[Try[Int]]] = {
      val action: DBIO[Seq[(Int, ProjectEntry)]] = ProjectEntry.getProjectsEligibleForStatusChange(newStatus, commissionId)

      dbConfig.db.run(action).flatMap { projectTuples =>
        if (projectTuples.isEmpty) {
          logger.info(s"No projects found needing status update to $newStatus for commission $commissionId")
          Future.successful(Seq.empty)
        } else {
          logger.info(s"Found ${projectTuples.length} projects to update to $newStatus for commission $commissionId")
          logger.info(s"Project IDs to update: ${projectTuples.map(_._1).mkString(", ")}")
          
          // Process updates sequentially using foldLeft
          projectTuples.foldLeft(Future.successful(Seq.empty[Try[Int]])) { case (accFuture, (id, project)) =>
            accFuture.flatMap { acc =>
              val updatedProject = project.copy(status = newStatus)
              
              val updateAction = (for {
                _ <- DBIO.successful(logger.info(s"Starting transaction for project $id"))
                updateCount <- TableQuery[ProjectEntryRow].filter(_.id === id).update(updatedProject)
                _ = logger.info(s"Database update for project $id completed with count: $updateCount")
                verification <- TableQuery[ProjectEntryRow].filter(_.id === id).result.headOption
                _ = verification match {
                  case Some(updated) if updated.status == newStatus => 
                    logger.info(s"Verified project $id is now status: ${updated.status}")
                  case Some(updated) => 
                    logger.warn(s"Project $id status mismatch - expected: $newStatus, actual: ${updated.status}")
                    throw new Exception(s"Project $id status mismatch - expected: $newStatus, actual: ${updated.status}")
                  case None =>
                    logger.error(s"Could not verify project $id - not found after update")
                    throw new Exception(s"Project $id not found after update")
                }
              } yield (updateCount, verification)).transactionally

              dbConfig.db.run(updateAction).map {
                case (count, Some(updated)) if updated.status == newStatus =>
                  val projectSerializer = new ProjectEntrySerializer {}
                  implicit val projectsWrites: Writes[ProjectEntry] = projectSerializer.projectEntryWrites
                  rabbitMqPropagator.tell(
                    ChangeEvent(Seq(projectsWrites.writes(updatedProject)), Some("project"), UpdateOperation()),
                    Actor.noSender
                  )
                  logger.info(s"Successfully updated project $id and sent to RabbitMQ")
                  acc :+ Success(id)
                case (_, Some(updated)) =>
                  logger.error(s"Project $id update verification failed - status mismatch")
                  acc :+ Failure(new Exception(s"Project $id update verification failed"))
                case (_, None) =>
                  logger.error(s"Project $id update verification failed - project not found")
                  acc :+ Failure(new Exception(s"Project $id not found after update"))
              }.recover {
                case err =>
                  logger.error(s"Failed to update project $id", err)
                  acc :+ Failure(err)
              }
            }
          }.map { results =>
            val (successes, failures) = results.partition(_.isSuccess)
            logger.info(s"Update complete: ${successes.length} successes, ${failures.length} failures")
            if (failures.nonEmpty) {
              logger.error(s"Failed project updates: ${failures.map(_.failed.get.getMessage).mkString(", ")}")
            }
            results
          }
        }
      }
    }
  }


