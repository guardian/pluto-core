package services

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq._
import com.rabbitmq.client.{AMQP, ShutdownSignalException}
import models.{MissingAssetFileEntry, MissingAssetFileEntryDAO}
import org.slf4j.LoggerFactory
import play.api.Configuration
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}
import org.apache.commons.codec.binary.StringUtils
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class MissingFile(project_id: String, file_path: String, file_name: String) extends Object

@Singleton
class MissingFilesReceiver @Inject()(config:Configuration, dbConfigProvider:DatabaseConfigProvider)(implicit system:ActorSystem, missingAssetFileEntryDAO: MissingAssetFileEntryDAO) {
  import io.circe.generic.auto._

  private val logger = LoggerFactory.getLogger(getClass)
  private val factory = new ConnectionFactory()
  logger.debug("Got Rabbit MQ connection factory")
  factory.setUri(config.get[String]("rabbitmq.uri"))
  val exchangeName = config.getOptional[String]("rabbitmq.missing_files_exchange").getOrElse("assetsweeper")

  private val connection = system.actorOf(ConnectionActor.props(factory), "missing-files-receiver-connection")
  logger.debug("Got Rabbit MQ connection actor, requesting subscriber")

  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("plutocore-missing-files-channel"))
  private implicit val db = dbConfigProvider.get[PostgresProfile].db

  val rmqFactory = new ConnectionFactory()
  rmqFactory.setUri(config.get[String]("rabbitmq.uri"))
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory, reconnectionDelay = 10.seconds), "pluto-core-missing-files")

  def loadEvent(body:Array[Byte]) = {
    for {
      bodyAsString <- Try { StringUtils.newStringUtf8(body)}.toEither
      parsedContent <- io.circe.parser.parse(bodyAsString)
      marshalledContent <- parsedContent.as[MissingFile]
    } yield marshalledContent
  }

  def makeConsumer(channel:Channel): DefaultConsumer = {
    new DefaultConsumer(channel) {
      override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = super.handleShutdownSignal(consumerTag, sig)

      override def handleCancel(consumerTag: String): Unit = super.handleCancel(consumerTag)

      override def handleCancelOk(consumerTag: String): Unit = super.handleCancelOk(consumerTag)

      override def handleConsumeOk(consumerTag: String): Unit = super.handleConsumeOk(consumerTag)

      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit = {
        loadEvent(body) match {
          case Left(err)=>
            logger.error(s"Received invalid message with key ${envelope.getRoutingKey} from exchange ${envelope.getExchange}: ${err.getMessage}", err)

          case Right(messageData)=>
            handleEvent(messageData).map({
              case true=>
                channel.basicAck(envelope.getDeliveryTag, false)
              case false=>
                logger.warn("Could not handle message, leaving it on-queue")
                channel.basicNack(envelope.getDeliveryTag, false, true)
            })
        }
      }

      def handleEvent(messageData: MissingFile): Future[Boolean] = {
        if (messageData.project_id.toInt.isValidInt) {
          if (messageData.file_path.startsWith("/")) {
            if (messageData.file_path.startsWith("/Volumes/Multimedia2/Media Production/Assets/Branding")) {
              logger.debug(s"'Missing file' at path: ${messageData.file_path} is branding. Ignoring.")
            } else {
              logger.debug(s"Missing file at path: ${messageData.file_path} is used by project ${messageData.project_id}")
              missingAssetFileEntryDAO.entryFor(messageData.file_path, messageData.project_id.toInt).map({
                case Success(filesList) =>
                  if (filesList.isEmpty) {
                    //No file entries exist already, create one and proceed
                    logger.debug(s"Attempting to create record for missing file used by project ${messageData.project_id} at path ${messageData.file_path}")
                    missingAssetFileEntryDAO.save(MissingAssetFileEntry(None, messageData.project_id.toInt, messageData.file_path))
                  } else {
                    logger.debug(s"Missing file used by project ${messageData.project_id} at path ${messageData.file_path} already logged in the database.")
                  }
                case Failure(error) =>
                  logger.debug(s"Could not load data on the missing file used by project ${messageData.project_id} at path ${messageData.file_path}. Error was $error")
              })
            }
          } else {
            logger.debug(s"Recorded 'missing file' at path: ${messageData.file_path} does not with a /. Ignoring.")
          }

          Future(true)
        } else Future(false)
      }

      override def handleRecoverOk(consumerTag: String): Unit = super.handleRecoverOk(consumerTag)
    }
  }

  def setupSubscriber(channel:Channel,  self:ActorRef) = {
    logger.debug(s"Setting up Rabbit MQ subscriber")

    logger.debug(s"Exchange name is $exchangeName")

    val maybeQueue = Try {
      channel
        .queueDeclare("missing-files", false, false, false, null)
        .getQueue
    }

    maybeQueue match {
      case Success(queue) =>
        logger.debug("Binding to exchange....")
        channel.queueBind(queue, exchangeName, "assetsweeper.premiere_get_referenced_media.missing_file")
        logger.debug("Initiating consumer...")
        channel.basicConsume(queue, makeConsumer(channel))
        logger.debug("Setup done")
      case Failure(err) =>
        logger.error(s"Could not declare queue, terminating: ${err.getMessage}", err)
        system
          .terminate()
          .andThen({
            case _=>
              Thread.sleep(2000)
              logger.error(s"pluto-core terminated because the setup of missing-files queue in Rabbit MQ was incorrect. Try deleting it and allowing pluto-core to startup again.")
              logger.error(s"Actual exception was: ${err.getMessage}", err)
          })
    }
  }

}
