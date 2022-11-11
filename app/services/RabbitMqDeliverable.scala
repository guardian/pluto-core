package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.Inject
import com.rabbitmq.client.AMQP.Exchange
import javax.inject.Singleton
import org.slf4j.LoggerFactory
import play.api.{Configuration, Logger}


object RabbitMqDeliverable {
  private val logger = LoggerFactory.getLogger(getClass)

  trait RabbitMqDeliverableEvent {
  }

  object DeliverableEvent {
    def apply(project_id: Int): DeliverableEvent = {
      new DeliverableEvent(project_id)
    }
  }

  case class DeliverableEvent(project_id: Int)
    extends RabbitMqDeliverableEvent
}

@Singleton
class RabbitMqDeliverable @Inject()(configuration:Configuration, system:ActorSystem) extends Actor {
  import RabbitMqDeliverable._
  import com.newmotion.akka.rabbitmq._
  import scala.concurrent.duration._

  val logger: Logger = Logger(getClass)
  val rmqFactory = new ConnectionFactory()
  rmqFactory.setUri(configuration.get[String]("rabbitmq.uri"))
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory, reconnectionDelay = 10.seconds), "pluto-core-deliverable")
  val rmqChannel: ActorRef = rmqConnection.createChannel(ChannelActor.props(channelSetup))
  val rmqRouteBase = configuration.getOptional[String]("rabbitmq.deliverable.route-base").getOrElse("deliverables.deliverable.pluto.core.delete")
  val rmqExchange = configuration.getOptional[String]("rabbitmq.deliverable.exchange").getOrElse("pluto-deliverables")

  def channelSetup(channel: Channel, self: ActorRef): Exchange.DeclareOk = {
    channel.exchangeDeclare(rmqExchange, "topic")
  }

  override def receive: Receive = {
    case event:DeliverableEvent =>
      logger.info(s"RabbitMqDeliverable is attempting to send a message to the queue.")
      val messageToSend: String  = s"""{"project_id":${event.project_id}}"""
      rmqChannel ! ChannelMessage(channel => channel.basicPublish(rmqExchange, rmqRouteBase, null, messageToSend.getBytes), dropIfNoChannel = false)
    case other:Any=>
      logger.error(s"RabbitMqDeliverable got an unexpected input: ${other}")
    case _=>
      logger.error(s"RabbitMqDeliverable got an unexpected input.")
  }
}


