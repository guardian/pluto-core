package services

import akka.actor.{Actor, ActorRef, Props}
import auth.BearerTokenAuth
import controllers.GenericDatabaseObjectControllerWithFilter
import models._
import play.api.cache.SyncCacheApi
import play.api.libs.json.{JsResult, JsValue, Json}
import play.api.mvc.{ControllerComponents, Request}
import slick.jdbc.PostgresProfile.api._

import java.util.UUID
import javax.inject.Named
import scala.concurrent.Future
import scala.util.Try
//import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.google.inject.Inject
import models.{EntryStatus, ProjectEntryRow}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

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
class CommissionStatusPropagator @Inject() (@Named("rabbitmq-propagator")
                                            cacheImpl:SyncCacheApi,
                                            implicit val rabbitMqPropagator:ActorRef,
                                            configuration:Configuration,
                                            dbConfigProvider:DatabaseConfigProvider)
  extends Actor
    with GenericDatabaseObjectControllerWithFilter[ProjectEntry,ProjectEntryFilterTerms]
    with ProjectEntrySerializer
    with ProjectRequestSerializer
    with ProjectEntryFilterTermsSerializer
{
  import CommissionStatusPropagator._
  import models.EntryStatusMapper._

  //override def persistenceId = "commission-status-propagator-" + self.path.name

  private final var state:CommissionStatusPropagatorState = CommissionStatusPropagatorState(Map())
  private final var restoreCompleted = false
  override val logger = Logger(getClass)

  protected val snapshotInterval = configuration.getOptional[Long]("pluto.persistence-snapshot-interval").getOrElse(50L)
  private implicit val db = dbConfigProvider.get[PostgresProfile].db

  override implicit val cache:SyncCacheApi = cacheImpl
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.info(s"Actor is about to restart due to: ${reason.getMessage}. The failed message was: ${message.getOrElse("")}")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    logger.info("Actor has been restarted.")
    super.postRestart(reason)
  }

  /**
   * add an event to the journal, and snapshot if required
   * @param event event to add
   */
  def updateState(event:CommissionStatusEvent): Unit = {
//    logger.debug(s"Marked event ${event.uuid} as pending")
//    state = state.updated(event)
//    if(lastSequenceNr % snapshotInterval ==0 && lastSequenceNr!=0)
//      saveSnapshot(state)
  }

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
//
//  override def receiveRecover: Receive = {
//    case evt:CommissionStatusEvent=>
//      updateState(evt)
//    case handledEvt:EventHandled =>
//      logger.debug(s"receiveRecover got message handled: ${handledEvt.uuid}")
//      state = state.removed(handledEvt.uuid)
//    case RecoveryCompleted=>
//      logger.info(s"Completed journal recovery, kicking off pending items")
//      restoreCompleted=true
//      state.foreach { stateEntry =>
//        logger.debug(s"${stateEntry._1.toString}: ${stateEntry._2.toString}")
//        self ! stateEntry._2
//      }
//    case SnapshotOffer(_, snapshot: CommissionStatusPropagatorState)=>
//      logger.debug("receiveRecover got snapshot offer")
//      state=snapshot
//  }

  override def validateFilterParams(request: Request[JsValue]): JsResult[ProjectEntryFilterTerms] = request.body.validate[ProjectEntryFilterTerms]

  override def preStart() = {
    logger.info("CommissionStatusPropagator started")
    super.preStart()
  }

  override def postStop() = {
    logger.info("CommissionStatusPropagator stopped")
    super.postStop()
  }

  override def receive: Receive = {
    case RetryFromState =>
      if (state.size != 0) logger.warn(s"CommissionStatusPropagator retrying ${state.size} events from state")

      state.foreach { stateEntry =>
        logger.warn(s"Retrying event ${stateEntry._1}")
        self ! stateEntry._2
      }

    case evt@CommissionStatusUpdate(commissionId, newStatus, uuid) =>
      val originalSender = sender()

      logger.info(s"$uuid: Received notification that commission $commissionId changed to $newStatus")

      val futureResult: Future[Seq[ProjectEntry]] = db.run(dbActionForStatusUpdate(newStatus, commissionId))

      futureResult.onComplete {
        case Failure(err) =>
          logger.error(s"Could not fetch project entries for $commissionId to $newStatus: ", err)
          originalSender ! akka.actor.Status.Failure(err)
        case Success(updatedProjects) =>
          logger.info(s"Project status change to $newStatus for ${updatedProjects.length} projects.")
          originalSender ! akka.actor.Status.Success(updatedProjects.length)

          if (updatedProjects.nonEmpty) {
            logger.info(s"Sending ${updatedProjects.length} updates to RabbitMQ.")
            updatedProjects.foreach { project =>
              sendToRabbitMq(UpdateOperation(), project.id.get, rabbitMqPropagator)
            }
          }

          confirmHandled(evt)
      }
  }

  def dbActionForStatusUpdate(newStatus: EntryStatus.Value, commissionId: Int): DBIO[Seq[ProjectEntry]] = newStatus match {
    case EntryStatus.Completed | EntryStatus.Killed =>
      val query = TableQuery[ProjectEntryRow]
        .filter(_.commission === commissionId)
        .filter(_.status =!= EntryStatus.Completed)
        .filter(_.status =!= EntryStatus.Killed)
      query.result.flatMap { projects =>
        DBIO.seq(projects.map(p => query.filter(_.id === p.id).map(_.status).update(newStatus)): _*).andThen(DBIO.successful(projects))
      }
    case EntryStatus.Held =>
      val query = TableQuery[ProjectEntryRow]
        .filter(_.commission === commissionId)
        .filter(_.status =!= EntryStatus.Completed)
        .filter(_.status =!= EntryStatus.Killed)
        .filter(_.status =!= EntryStatus.Held)
      query.result.flatMap { projects =>
        DBIO.seq(projects.map(p => query.filter(_.id === p.id).map(_.status).update(newStatus)): _*).andThen(DBIO.successful(projects))
      }
    case _ => DBIO.successful(Seq.empty[ProjectEntry])
  }

  def updateProjectStatusDBIO(projectId: Int, newStatus: EntryStatus.Value): DBIO[Any] = {
    val query = TableQuery[ProjectEntryRow].filter(_.id === projectId)
    for {
      _ <- query.map(_.status).update(newStatus)
      updatedProject <- query.result.head
    } yield updatedProject
  }



  //  override def receive: Receive = {
//    /**
//      * re-run any messages stuck in the actor's state. This is sent at 5 minute intervals by ClockSingleton and
//      * is there to ensure that events get retried (e.g. one instance loses network connectivity before postgres update is sent,
//      * it is restarted, so another instance will pick up the update)
//      */
//    case RetryFromState=>
//      if(state.size!=0) logger.warn(s"CommissionStatusPropagator retrying ${state.size} events from state")
//
//      state.foreach { stateEntry=>
//        logger.warn(s"Retrying event ${stateEntry._1}")
//        self ! stateEntry._2
//      }
//
//    case evt@CommissionStatusUpdate(commissionId, newStatus, uuid)=>
//      val originalSender = sender()
//
//        logger.info(s"${uuid}: Received notification that commission $commissionId changed to $newStatus")
//        val maybeRequiredUpdate = newStatus match {
//          case EntryStatus.Completed | EntryStatus.Killed=>
//            val q = for { project <- TableQuery[ProjectEntryRow]
//                              .filter(_.commission === commissionId)
//                              .filter(_.status =!= EntryStatus.Completed)
//                              .filter(_.status =!= EntryStatus.Killed)
//                          } yield project
//            Some(q.result)
//          case EntryStatus.Held=>
//            val q = for { project <- TableQuery[ProjectEntryRow]
//                              .filter(_.commission === commissionId)
//                              .filter(_.status =!= EntryStatus.Completed)
//                              .filter(_.status =!= EntryStatus.Killed)
//                              .filter(_.status =!= EntryStatus.Held)
//                          } yield project
//            Some(q.result)
//          case _=>None
//        }
//
//      maybeRequiredUpdate match {
//        case Some(projectAction) =>
//          val futureResult: Future[Either[Throwable, Int]] =
//            db.run(projectAction.transactionally)
//          . flatMap ({
//            case Failure(err) =>
//              logger.error(s"Could not fetch project entries for $commissionId to $newStatus: ", err)
//              originalSender ! akka.actor.Status.Failure(err)
//            case Success(projects) =>
//              projects.foreach { project =>
//                val update = for {
//                  p <- TableQuery[ProjectEntryRow].filter(_.id === project.id)
//                } yield p.status
//                val updateAction = update.update(newStatus)
//                db.run(updateAction).onComplete {
//                  case Failure(updateErr) =>
//                    logger.error(s"Could not update project status for project ${project.id} to $newStatus: ", updateErr)
//                  case Success(_) =>
//                    logger.info(s"Project ${project.id} status change to $newStatus")
//                    project.id.map { id =>
//                      sendToRabbitMq(UpdateOperation(), id, rabbitMqPropagator)
//                    }
//                }
//              }
//              originalSender ! akka.actor.Status.Success(projects.size)
//              confirmHandled(evt)
//          })
//        case None =>
//          logger.info(s"Commission $commissionId status change to $newStatus did not need any project updates")
//          originalSender ! akka.actor.Status.Success(0)
//          confirmHandled(evt)
//      }
//      }

  /**
    * Implement this method in your subclass to validate that the incoming record (passed in request) does indeed match
    * your case class.
    * Normally this can be done by simply returning: request.body.validate[YourCaseClass]. apparently this can't be done in the trait
    * because a concrete serializer implementation must be available at compile time, which would be for [YourCaseClass] but not for [M]
    *
    * @param request Play request object
    * @return JsResult representing a validation success or failure.
    */
  override def validate(request: Request[JsValue]): JsResult[ProjectEntry] = ???

  /**
    * Implement this method in your subclass to return a Future of all matching records
    *
    * @param startAt start database retrieval at this record
    * @param limit   limit number of returned items to this
    * @return Future of Try of Sequence of record type [[M]]
    */
  override def selectall(startAt: Int, limit: Int): Future[Try[(Int, Seq[ProjectEntry])]] = ???

  /**
    * Implement this method in your subclass to return a Future of all records that match the given filter terms.
    * Errors should be returned as a Failure of the provided Try
    *
    * @param startAt start database retrieval at this record
    * @param limit   limit number of returned items to this
    * @param terms   case class of type [[F]] representing the filter terms
    * @return Future of Try of Sequence of record type [[M]]
    */

override def selectFiltered(startAt: Int, limit: Int, terms: ProjectEntryFilterTerms): Future[Try[(Int, Seq[ProjectEntry])]] = Future.failed(new NotImplementedError("insert method is not implemented"))
override def selectid(requestedId: Int): Future[Try[Seq[ProjectEntry]]] = Future.failed(new NotImplementedError("insert method is not implemented"))
override def deleteid(requestedId: Int): Future[Try[Int]] = Future.failed(new NotImplementedError("insert method is not implemented"))

  override def jstranslate(result: Seq[ProjectEntry]): Json.JsValueWrapper = result

  override def jstranslate(result: ProjectEntry): Json.JsValueWrapper = result //implicit translation should handle this

  /*this is pointless because of the override of [[create]] below, so it should not get called,
   but is needed to conform to the [[GenericDatabaseObjectController]] protocol*/
  override def insert(entry: ProjectEntry, uid: String) = Future(Failure(new RuntimeException("ProjectEntryController::insert should not have been called")))


  override def dbupdate(itemId: Int, entry: ProjectEntry): Future[Try[Int]] = Future.failed(new NotImplementedError("insert method is not implemented"))

  override protected def controllerComponents: ControllerComponents = throw new NotImplementedError("controllerComponents is not implemented")

  override implicit val cache: SyncCacheApi = throw new NotImplementedError("cache is not implemented")

  override val bearerTokenAuth: BearerTokenAuth = throw new NotImplementedError("bearerTokenAuth is not implemented")

  override implicit val config: Configuration =  throw new NotImplementedError("config is not implemented")

}
