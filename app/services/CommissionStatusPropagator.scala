package services

import akka.actor.{Actor, ActorRef, Props}
import auth.BearerTokenAuth
import controllers.{GenericDatabaseObjectControllerWithFilter, ProjectEntryController}
import models.{ProjectEntryFilterTerms, ProjectEntryFilterTermsSerializer, ProjectEntrySerializer, ProjectRequestSerializer}
import play.api.cache.SyncCacheApi
import play.api.libs.json.{JsResult, JsValue, Json}
import play.api.mvc.{ControllerComponents, Request}

import java.util.UUID
import javax.inject.Named
import scala.concurrent.Future
import scala.util.Try
//import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.google.inject.Inject
import models.{EntryStatus, ProjectEntry, ProjectEntryRow}
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
class CommissionStatusPropagator @Inject() (projectdb:ProjectEntryController, configuration:Configuration, override implicit val config: Configuration, dbConfigProvider:DatabaseConfigProvider, cacheImpl:SyncCacheApi, @Named("rabbitmq-propagator") rabbitMqPropagator: ActorRef, override val controllerComponents:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth) extends Actor with GenericDatabaseObjectControllerWithFilter[ProjectEntry,ProjectEntryFilterTerms]
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

    override def jstranslate(result: Seq[ProjectEntry]): Json.JsValueWrapper = result

    override def jstranslate(result: ProjectEntry): Json.JsValueWrapper = result //implicit translation should handle this

    /*this is pointless because of the override of [[create]] below, so it should not get called,
     but is needed to conform to the [[GenericDatabaseObjectController]] protocol*/
    override def insert(entry: ProjectEntry, uid: String) = Future(Failure(new RuntimeException("ProjectEntryController::insert should not have been called")))

    override def validate(request: Request[JsValue]) = request.body.validate[ProjectEntry]

    override def validateFilterParams(request: Request[JsValue]): JsResult[ProjectEntryFilterTerms] = request.body.validate[ProjectEntryFilterTerms]

    override def selectall(startAt: Int, limit: Int) = dbConfig.db.run(
      TableQuery[ProjectEntryRow].length.result.zip(
        TableQuery[ProjectEntryRow].sortBy(_.created.desc).drop(startAt).take(limit).result
      )
    ).map(Success(_)).recover(Failure(_))

    val dbConfig = dbConfigProvider.get[PostgresProfile]
    implicit val implicitConfig = config

    override def deleteid(requestedId: Int) = dbConfig.db.run(
      TableQuery[ProjectEntryRow].filter(_.id === requestedId).delete.asTry
    )

    override def selectid(requestedId: Int): Future[Try[Seq[ProjectEntry]]] = dbConfig.db.run(
      TableQuery[ProjectEntryRow].filter(_.id === requestedId).result.asTry
    )

    override def selectFiltered(startAt: Int, limit: Int, terms: ProjectEntryFilterTerms): Future[Try[(Int, Seq[ProjectEntry])]] = {
      val basequery = terms.addFilterTerms {
        TableQuery[ProjectEntryRow]
      }

      dbConfig.db.run(
        basequery.length.result.zip(
          basequery.sortBy(_.created.desc).drop(startAt).take(limit).result
        )
      ).map(Success(_)).recover(Failure(_))
    }

    override def dbupdate(itemId: Int, entry: ProjectEntry): Future[Try[Int]] = {
      val newRecord = entry.id match {
        case Some(id) => entry
        case None => entry.copy(id = Some(itemId))
      }

      dbConfig.db.run(TableQuery[ProjectEntryRow].filter(_.id === itemId).update(newRecord).asTry)
        .map(rows => {
          sendToRabbitMq(UpdateOperation(), itemId, rabbitMqPropagator)
          rows
        })
    }

    override implicit val cache:SyncCacheApi = cacheImpl

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

  override def receive: Receive = {
    case RetryFromState =>
      if (state.size != 0) logger.warn(s"CommissionStatusPropagator retrying ${state.size} events from state")

      state.foreach { stateEntry =>
        logger.warn(s"Retrying event ${stateEntry._1}")
        self ! stateEntry._2
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
  //}
}
