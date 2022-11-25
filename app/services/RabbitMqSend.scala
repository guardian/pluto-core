package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.Inject
import com.rabbitmq.client.AMQP.Exchange
import javax.inject.Singleton
import org.slf4j.LoggerFactory
import play.api.{Configuration, Logger}


object RabbitMqSend {
  private val logger = LoggerFactory.getLogger(getClass)

  trait RabbitMqSendEvent {
  }

  object FixEvent {
    def apply(is_dir: Boolean, is_file: Boolean, filename: String, parent_dir: String): FixEvent = {
      new FixEvent(is_dir, is_file, filename, parent_dir)
    }
  }

  case class FixEvent(is_dir: Boolean, is_file: Boolean, filename: String, parent_dir: String)
    extends RabbitMqSendEvent
}

@Singleton
class RabbitMqSend @Inject()(configuration:Configuration, system:ActorSystem) extends Actor {
  import RabbitMqSend._
  import com.newmotion.akka.rabbitmq._
  import scala.concurrent.duration._

  val logger: Logger = Logger(getClass)
  val rmqFactory = new ConnectionFactory()
  rmqFactory.setUri(configuration.get[String]("rabbitmq.uri"))
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory, reconnectionDelay = 10.seconds), "pluto-core-fix")
  val rmqChannel: ActorRef = rmqConnection.createChannel(ChannelActor.props(channelSetup))
  val rmqRouteBase = configuration.getOptional[String]("rabbitmq.fix.route-base").getOrElse("assetsweeper.asset_folder_importer.file.permissions_problem")
  val rmqExchange = configuration.getOptional[String]("rabbitmq.fix.exchange").getOrElse("assetsweeper")

  def channelSetup(channel: Channel, self: ActorRef): Exchange.DeclareOk = {
    channel.exchangeDeclare(rmqExchange, "topic", true)
  }

  override def receive: Receive = {
    case event:FixEvent =>
      logger.info(s"RabbitMqSend is attempting to send a message to the queue.")
      val messageToSend: String  = s"""{"is_dir":${event.is_dir},"is_file":${event.is_file},"filename":"${event.filename}","parent_dir":"${event.parent_dir}"}"""
      rmqChannel ! ChannelMessage(channel => channel.basicPublish(rmqExchange, rmqRouteBase, null, messageToSend.getBytes), dropIfNoChannel = false)
    case other:Any=>
      logger.error(s"RabbitMqSend got an unexpected input: ${other}")
    case _=>
      logger.error(s"RabbitMqSend got an unexpected input.")
  }
}


