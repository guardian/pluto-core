package services

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq._
import com.rabbitmq.client.{AMQP, ShutdownSignalException}
import models.{PlutoCommission, PlutoCommissionRow}
import org.slf4j.LoggerFactory
import play.api.Configuration

import javax.inject.{Inject, Named, Singleton}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import org.apache.commons.codec.binary.StringUtils
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

case class MissingCommission(id: Int) extends Object

@Singleton
class AtomResponderReceiver @Inject()(config:Configuration, dbConfigProvider:DatabaseConfigProvider)(implicit system:ActorSystem) {
  import io.circe.generic.auto._

  private val logger = LoggerFactory.getLogger(getClass)
  private val factory = new ConnectionFactory()
  logger.debug("Got Rabbit MQ connection factory")
  factory.setUri(config.get[String]("rabbitmq.uri"))
  val exchangeName = config.getOptional[String]("rabbitmq.atomresponder_exchange").getOrElse("pluto-atomresponder")

  //Get a connection actor
  private val connection = system.actorOf(ConnectionActor.props(factory), "atomresponder-receiver-connection")
  logger.debug("Got Rabbit MQ connection actor, requesting subscriber")
  //Configure ourselves as a subscriber
  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("plutocore-atomresponder-channel"))
  private implicit val db = dbConfigProvider.get[PostgresProfile].db


  def loadEvent(body:Array[Byte]) = {
    for {
      bodyAsString <- Try { StringUtils.newStringUtf8(body)}.toEither
      parsedContent <- io.circe.parser.parse(bodyAsString)
      marshalledContent <- parsedContent.as[MissingCommission]
    } yield marshalledContent
  }

  def getCommission(commissionId: Option[Int]):Future[Option[PlutoCommission]] = {
    commissionId match {
      case None=>Future(None)
      case Some(commId)=>
        db.run(
          TableQuery[PlutoCommissionRow].filter(_.id===commId).result.asTry
        ).map({
          case Success(matchingEntries)=>matchingEntries.headOption
          case Failure(error)=>throw error
        })
    }

  }

  def makeConsumer(channel:Channel): DefaultConsumer = {
    new DefaultConsumer(channel) {
      //note - docs say to avoid long-running code here because it delays dispatch of other messages on the same connection
      override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = super.handleShutdownSignal(consumerTag, sig)

      override def handleCancel(consumerTag: String): Unit = super.handleCancel(consumerTag)

      override def handleCancelOk(consumerTag: String): Unit = super.handleCancelOk(consumerTag)

      override def handleConsumeOk(consumerTag: String): Unit = super.handleConsumeOk(consumerTag)

      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit = {
        //logger.info(s"${channel}")
        logger.info(s"${envelope}")
        loadEvent(body) match {
          case Left(err)=>
            logger.error(s"Received invalid message with key ${envelope.getRoutingKey} from exchange ${envelope.getExchange}: ${err.getMessage}", err)

          case Right(messageData)=>
            handleEvent(consumerTag, envelope.getRoutingKey, messageData).map({
              case true=>
                channel.basicAck(envelope.getDeliveryTag, false)
              case false=>
                logger.warn("Could not handle message, leaving it on-queue")
                channel.basicNack(envelope.getDeliveryTag, false, true)
            })
        }
      }

      def handleEvent(consumerTag: String, routingKey: String, messageData: MissingCommission): Future[Boolean] = {
        logger.info(s"Key is ${routingKey}")
        if (messageData.id.isValidInt) {
          logger.info(s"Missing commission id. is ${messageData.id}")
          val commissionDataObject = getCommission(Some(messageData.id))
          commissionDataObject.onComplete({
            case Success(commissionData) => {
              logger.info(s"${commissionData}")
            }
            case Failure(exception) => {
              logger.info(s"${exception}")
            }
          })

          Future(true)
        } else Future(false)
      }

      override def handleRecoverOk(consumerTag: String): Unit = super.handleRecoverOk(consumerTag)
    }
  }

  def setupSubscriber(channel:Channel,  self:ActorRef) = {
    logger.debug(s"Setting up Rabbit MQ subscriber")

    logger.debug(s"Exchange name is $exchangeName")

    val queueArgs = Map[String, Object](
      "x-message-ttl"-> 60000.asInstanceOf[Object],
    )

    val maybeQueue = Try {
      channel
        .queueDeclare("missing-commissions", true, false, false, queueArgs.asJava)
        .getQueue
    }

    maybeQueue match {
      case Success(queue) =>
        logger.debug("Binding to exchange....")
        channel.queueBind(queue, exchangeName, "atomresponder.commission.#")
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
              logger.error(s"pluto-core terminated because the setup of atomresponder-receiver queue in Rabbit MQ was incorrect. Try deleting it and allowing pluto-core to startup again.")
              logger.error(s"Actual exception was: ${err.getMessage}", err)
          })
    }
  }

}
