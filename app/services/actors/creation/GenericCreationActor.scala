package services.actors.creation

import java.sql.Timestamp
import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence._
import models.{FileEntry, PostrunAction, ProjectEntry, ProjectRequestFull}
import org.slf4j.LoggerFactory
import play.api.Logger
import play.api.inject.Injector
import services.JacksonSerializable

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration, durationToPair}
import scala.reflect.{ClassTag, classTag}
import scala.util.{Failure, Success, Try}

object GenericCreationActor {
  val logger = LoggerFactory.getLogger(getClass)
  def props = Props[GenericCreationActor]

  trait CreationEvent {
    val rq: CreationMessage
    val eventId: UUID
  }

  case class ProjectCreateTransientData(destFileEntry: Option[FileEntry], createdProjectEntry: Option[ProjectEntry], postrunSequence: Option[Seq[PostrunAction]]) extends JacksonSerializable
  /**
    * This message is sent to each actor in the chain when creating a new project.  Each should do its work and send the result
    * back to the sender
    * @param rq [[ProjectRequestFull]] model describing the project to be created
    */
  case class NewProjectRequest(rq:ProjectRequestFull, createTime:Option[LocalDateTime], data:ProjectCreateTransientData) extends CreationMessage with JacksonSerializable

  /**
    * This message is send to each actor in the chain when the project creation has failed.  Each should undo the work that was previously
    * done in response to [[NewProjectRequest]]
    * @param rq [[ProjectRequestFull]] model describing the project being rolled back
    */
  case class NewProjectRollback(rq:ProjectRequestFull, data:ProjectCreateTransientData) extends CreationMessage with JacksonSerializable

  case class ProjectCreateFailed(rq:ProjectRequestFull, error:Throwable) extends CreationMessage with JacksonSerializable
  case class ProjectCreateSucceeded(rq:ProjectRequestFull, createdProjectEntry:ProjectEntry) extends CreationMessage with JacksonSerializable

  case class StepSucceded(updatedData:ProjectCreateTransientData) extends CreationMessage with JacksonSerializable

  case class StepFailed(updatedData:ProjectCreateTransientData, err:Throwable) extends CreationMessage with JacksonSerializable

  case class NewCreationEvent(rq:CreationMessage, eventId:UUID) extends CreationEvent with JacksonSerializable

  case class CreateEventHandled(eventId: UUID)  extends JacksonSerializable

  val extractEntityId:ShardRegion.ExtractEntityId = {
    case msg@ NewProjectRequest(rq,_,_)=>(rq.filename + "-f", msg)
    case msg@ NewProjectRollback(rq,_)=>(rq.filename + "-r", msg)
  }

  val maxShards = 100

  val extractShardId:ShardRegion.ExtractShardId = {
    case NewProjectRequest(rq,_,_) => ((rq.filename + "-f").hashCode%100).toString
    case NewProjectRollback(rq,_) => ((rq.filename + "-r").hashCode%100).toString
  }

}

trait GenericCreationActor extends PersistentActor {
  protected val logger=Logger(getClass)

  var state = CreationStepState()

  protected val snapshotInterval = 50

  import GenericCreationActor._
  import context.dispatcher //get an execution context for futures

  /**
    * add an event to the journal, and snapshot if required
    * @param event event to add
    */
  def updateState(event:CreationEvent): Unit = {
    state = state.updated(event)
    if(lastSequenceNr % snapshotInterval ==0 && lastSequenceNr!=0)
      saveSnapshot(state)
  }

  def doPersistedAsync(msg:CreationMessage)(block: (CreationMessage,ActorRef)=>Future[Any]): Unit = {
    val newUuid = UUID.randomUUID()
    persist(NewCreationEvent(msg,newUuid)){event=>
      updateState(event)
      logger.debug(s"persisted generic request event with uuid $newUuid to journal, now performing")
      val originalSender = sender()
      block(msg,originalSender).andThen({
        case Success(result)=>
          logger.debug(s"create event $newUuid has been handled")
          self ! CreateEventHandled(newUuid)
        case Failure(err)=> //should probably seperate errors into Recoverable and Nonrecoverable subclasses
          logger.error("Creation step failed", err)
      })
    }
  }

  def doPersistedSync(msg:CreationMessage)(block: (CreationMessage,ActorRef)=>Try[Any]) = {
    val newUuid = UUID.randomUUID()
    persist(NewCreationEvent(msg,newUuid)){event=>
      updateState(event)
      logger.debug("persisted generic request event to journal, now performing")
      val originalSender = sender()
      block(msg,originalSender) match {
        case Success(result)=>
          self ! CreateEventHandled(newUuid)
        case Failure(err)=> //should probably seperate errors into Recoverable and Nonrecoverable subclasses
          logger.error("Creation step failed", err)
      }
    }
  }

  override def receiveRecover:Receive = {
    case evt:CreationEvent =>
      logger.debug(s"receiveRecover got message event: $evt")
      updateState(evt)
    case handledEvt:CreateEventHandled =>
      logger.debug(s"receiveRecover got message handled: ${handledEvt.eventId}")
      state = state.removed(handledEvt.eventId)
    case RecoveryCompleted=>
      logger.info(s"Completed journal recovery")
    case SnapshotOffer(_, snapshot: CreationStepState)=>
      logger.debug("receiveRecover got snapshot offer")
      state=snapshot
  }

  override def receiveCommand: Receive = {
    case SaveSnapshotSuccess(metadata)=>
      logger.debug(s"Successfully saved snapshot: $metadata")
      logger.debug(s"Now removing messages to sequence no ${metadata.sequenceNr} from journal")
      deleteMessages(metadata.sequenceNr)
    case SaveSnapshotFailure(metadata,error)=>
      logger.error(s"Could not save snapshot ${metadata.sequenceNr} for ${metadata.persistenceId}: ",error)
//    case msgAsObject:CreationMessage=>
//      persist(NewCreationEvent(msgAsObject, UUID.randomUUID())) { event=>
//        updateState(event)
//        logger.debug("persisted creation event to journal, now sending")
//        self ! event
//      }
  }
}
