package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.Inject
import com.rabbitmq.client.AMQP.{Exchange, BasicProperties}
import mes.OnlineOutputMessage
import javax.inject.Singleton
import org.slf4j.LoggerFactory
import play.api.{Configuration, Logger}
import java.util.UUID


object RabbitMqMatrix {
  private val logger = LoggerFactory.getLogger(getClass)

  trait RabbitMqMatrixEvent {
  }

  object MatrixEvent {
    def apply(message: OnlineOutputMessage): MatrixEvent = {
      new MatrixEvent(message)
    }
  }

  case class MatrixEvent(message: OnlineOutputMessage)
    extends RabbitMqMatrixEvent
}

@Singleton
class RabbitMqMatrix @Inject()(configuration:Configuration, system:ActorSystem) extends Actor {
  import RabbitMqMatrix._
  import com.newmotion.akka.rabbitmq._
  import scala.concurrent.duration._

  val logger: Logger = Logger(getClass)
  val rmqFactory = new ConnectionFactory()
  rmqFactory.setUri(configuration.get[String]("rabbitmq.uri"))
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory, reconnectionDelay = 10.seconds), "pluto-core-matrix")
  val rmqChannel: ActorRef = rmqConnection.createChannel(ChannelActor.props(channelSetup))
  val rmqRouteBase = configuration.getOptional[String]("rabbitmq.matrix.route-base").getOrElse("storagetier.restorer.media_not_required.nearline")
  val rmqExchange = configuration.getOptional[String]("rabbitmq.matrix.exchange").getOrElse("storagetier-project-restorer")

  def channelSetup(channel: Channel, self: ActorRef): Exchange.DeclareOk = {
    channel.exchangeDeclare(rmqExchange, "topic", true)
  }

  override def receive: Receive = {
    case event:MatrixEvent =>
      logger.info(s"RabbitMqMatrix is attempting to send a message to the queue.")
      var originalPath = ""
      if (event.message.originalFilePath.isDefined) {
        originalPath = event.message.originalFilePath.get
      }
      var fileSize: Long = 0
      if (event.message.fileSize.isDefined) {
        fileSize = event.message.fileSize.get
      }
      var nearlineId = ""
      if (event.message.nearlineId.isDefined) {
        nearlineId = event.message.nearlineId.get
      }
      var projectIdsString = s"["
      event.message.projectIds.foreach(project => {
        projectIdsString = s"""$projectIdsString"$project","""
      })
      projectIdsString = projectIdsString.dropRight(1)
      projectIdsString = s"$projectIdsString]"
      val messageToSend: String = s"""{"mediaTier":"${event.message.mediaTier}","projectIds":${projectIdsString},"originalFilePath":"$originalPath","fileSize":$fileSize,"vidispineItemId":"${event.message.vidispineItemId.get}","nearlineId":"${nearlineId}","mediaCategory":"${event.message.mediaCategory}"}"""
      val msgProps = new BasicProperties.Builder()
        .contentType("application/json")
        .contentEncoding("UTF-8")
        .messageId(UUID.randomUUID().toString)
        .build()
      rmqChannel ! ChannelMessage(channel => channel.basicPublish(rmqExchange, rmqRouteBase, msgProps, messageToSend.getBytes), dropIfNoChannel = false)
    case other:Any=>
      logger.error(s"RabbitMqMatrix got an unexpected input: ${other}")
    case _=>
      logger.error(s"RabbitMqMatrix got an unexpected input.")
  }
}