package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.Inject
import com.rabbitmq.client.AMQP.Exchange
import javax.inject.Singleton
import models.{PlutoCommission, PlutoModel, PlutoWorkingGroup}
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
 */
@Singleton
class RabbitMqPropagator @Inject()(configuration:Configuration, system:ActorSystem) extends Actor {
  import RabbitMqPropagator._
  import com.newmotion.akka.rabbitmq._
  import scala.concurrent.duration._

  val logger: Logger = Logger(getClass)
  val rmqFactory = new ConnectionFactory()
  rmqFactory.setUri(configuration.get[String]("pluto.rabbitmq-uri"))
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory, reconnectionDelay = 10.seconds), "pluto-core")
  val rmqChannel: ActorRef = rmqConnection.createChannel(ChannelActor.props(channelSetup))
  val rmqRouteBase = "core"
  val rmqExchange = "pluto-core-model"

  def channelSetup(channel: Channel, self: ActorRef): Exchange.DeclareOk = {
    channel.exchangeDeclare(rmqExchange, "topic")
  }

  override def receive: Receive = {
    /**
     * re-run any messages stuck in the actor's state. This is sent at 5 minute intervals by ClockSingleton and
     * is there to ensure that events get retried (e.g. one instance loses network connectivity before postgres update is sent,
     * it is restarted, so another instance will pick up the update)
     */

    case ev@ChangeEvent(model: PlutoModel, operation) =>
      val route = getRoute(model, operation)
      rmqChannel ! ChannelMessage(channel => channel.basicPublish(rmqExchange, route, null, ev.json.getBytes), dropIfNoChannel = false)
      sender() ! akka.actor.Status.Success(())
  }

  private def getRoute(model: PlutoModel, operation: ChangeOperation): String = {
    val modelPath = model match {
      case _: PlutoCommission => "commission"
      case _: PlutoWorkingGroup => "workinggroup"
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
