package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.Inject
import play.api.{Configuration, Logger}


object RabbitMqPropagator {

  trait RabbitMqEvent {
  }

  case class ChangeEvent(modelType: PlutoModel, operation: ChangeOperation, body: String) extends RabbitMqEvent with
    JacksonSerializable

  object ChangeEvent {
    def apply(modelType: PlutoModel, operation: ChangeOperation, body: String) = new ChangeEvent(modelType, operation, body)
  }

}
/**
 * Propagates model changes to rabbit mq for others to consume.
 *
 * @param configuration
 */
class RabbitMqPropagator @Inject()(configuration:Configuration, system:ActorSystem) extends Actor {
  import RabbitMqPropagator._
  import com.newmotion.akka.rabbitmq._

  val logger = Logger(getClass)
  val rmqFactory = new ConnectionFactory()
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory), "pluto-core")
  val rmqChannel: ActorRef = rmqConnection.createChannel(ChannelActor.props(channelSetup))
  val rmqRouteBase = "core"

  def channelSetup(channel: Channel, self: ActorRef) = {
    for (modelType <- List(PlutoCommissionType)) {
      for (operation <- List(CreateOperation, UpdateOperation)) {
        channel.queueDeclare(getQueue(modelType, operation), false, false, false, null)
      }
    }
  }

  override def receive: Receive = {
    /**
     * re-run any messages stuck in the actor's state. This is sent at 5 minute intervals by ClockSingleton and
     * is there to ensure that events get retried (e.g. one instance loses network connectivity before postgres update is sent,
     * it is restarted, so another instance will pick up the update)
     */

    case ChangeEvent(modelType, operation, body) =>
      val queue = getQueue(modelType, operation)
      rmqChannel ! ChannelMessage(channel => channel.basicPublish("", queue, null, body.getBytes), dropIfNoChannel = false)
      logger.info("RabbitMQ sent")
  }

  private def getQueue(model: PlutoModel, operation: ChangeOperation): String = {
    val operationPath = operation match {
      case CreateOperation => "create"
      case UpdateOperation => "update"
    }

    val modelPath = model match {
      case PlutoCommissionType => "commisssion"
    }

    List(rmqRouteBase, modelPath, operationPath).mkString(".")
  }
}

sealed trait ChangeOperation
case object CreateOperation extends ChangeOperation
case object UpdateOperation extends ChangeOperation

sealed trait PlutoModel
case object PlutoCommissionType extends PlutoModel