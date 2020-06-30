package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.Inject
import models.{PlutoCommission, PlutoModel}
import play.api.libs.json.{Json, Writes}
import play.api.{Configuration, Logger}


object RabbitMqPropagator {

  trait RabbitMqEvent {
  }

  case class ChangeEvent[M<:PlutoModel](model: M, operation: ChangeOperation)(implicit writes: Writes[M])
    extends RabbitMqEvent with JacksonSerializable {
    val json: String = Json.stringify(writes.writes(model))
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
    for (modelType <- List("commission")) {
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

    case ev@ChangeEvent(model: PlutoModel, operation) =>
      val route = getRoute(model, operation)
      rmqChannel ! ChannelMessage(channel => channel.basicPublish("", route, null, ev.json.getBytes), dropIfNoChannel = false)
      logger.info("RabbitMQ sent")
  }

  private def getQueue(model: String, operation: ChangeOperation): String = {
    List(rmqRouteBase, model, operationPath(operation)).mkString(".")
  }

  private def getRoute(model: PlutoModel, operation: ChangeOperation): String = {
    val modelPath = model match {
      case _: PlutoCommission => "commisssion"
    }

    List(rmqRouteBase, modelPath, operationPath(operation)).mkString(".")
  }

  def operationPath(operation: ChangeOperation): String = operation match {
    case CreateOperation => "create"
    case UpdateOperation => "update"
  }
}

sealed trait ChangeOperation
case object CreateOperation extends ChangeOperation
case object UpdateOperation extends ChangeOperation
