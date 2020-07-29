package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.Inject
import com.rabbitmq.client.AMQP.Exchange
import javax.inject.Singleton
import models.{PlutoCommission, PlutoModel, PlutoWorkingGroup, ProjectEntry}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.{Configuration, Logger}


object RabbitMqPropagator {

  trait RabbitMqEvent {
  }

  case class ChangeEvent(content: JsValueWrapper, itemType: Option[String], operation: ChangeOperation)
    extends RabbitMqEvent with JacksonSerializable {
    def json: String = {
      Json.stringify(Json.arr(content))
    }
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
  rmqFactory.setUri(configuration.get[String]("rabbitmq.uri"))
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory, reconnectionDelay = 10.seconds), "pluto-core")
  val rmqChannel: ActorRef = rmqConnection.createChannel(ChannelActor.props(channelSetup))
  val rmqRouteBase = configuration.getOptional[String]("rabbitmq.route-base").getOrElse("core")
  val rmqExchange = configuration.getOptional[String]("rabbitmq.exchange").getOrElse("pluto-core")

  def channelSetup(channel: Channel, self: ActorRef): Exchange.DeclareOk = {
    channel.exchangeDeclare(rmqExchange, "topic")
  }

  override def receive: Receive = {
    /**
     * re-run any messages stuck in the actor's state. This is sent at 5 minute intervals by ClockSingleton and
     * is there to ensure that events get retried (e.g. one instance loses network connectivity before postgres update is sent,
     * it is restarted, so another instance will pick up the update)
     */

    case ev:ChangeEvent =>
      ev.itemType match {
        case Some(itemtype) =>
          val route = s"$rmqRouteBase.$itemtype.${operationPath(ev.operation)}"
          rmqChannel ! ChannelMessage(channel => channel.basicPublish(rmqExchange, route, null, ev.json.getBytes), dropIfNoChannel = false)
          sender() ! akka.actor.Status.Success(())
        case None =>
          logger.error("Unknown object type for rabbitmq propagation")
          sender() ! akka.actor.Status.Failure(new IllegalArgumentException("Unknown object type for rabbitmq propagation"))
      }

    case other:Any=>
      logger.error(s"RabbitMQPropagator got an unexpected message: ${other}")
  }

  private def getRoute(model: PlutoModel, operation: ChangeOperation): Option[String] = {
    val modelPath: Option[String] = model match {
      case _: PlutoCommission => Some("commission")
      case _: PlutoWorkingGroup => Some("workinggroup")
      case _: ProjectEntry => Some("project")
      case _ => None
    }

    modelPath.map(mp => List(rmqRouteBase, mp, operationPath(operation)).mkString("."))
  }

  def operationPath(operation: ChangeOperation): String = operation match {
    case CreateOperation => "create"
    case UpdateOperation => "update"
  }
}

sealed trait ChangeOperation
case object CreateOperation extends ChangeOperation
case object UpdateOperation extends ChangeOperation
