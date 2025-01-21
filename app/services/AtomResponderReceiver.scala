package services

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq._
import com.rabbitmq.client.AMQP.Exchange
import com.rabbitmq.client.{AMQP, ShutdownSignalException}
import models.{PlutoCommission, PlutoCommissionRow}
import org.slf4j.LoggerFactory
import play.api.Configuration
import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import org.apache.commons.codec.binary.StringUtils
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import scala.concurrent.duration._
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

  private val connection = system.actorOf(ConnectionActor.props(factory), "atomresponder-receiver-connection")
  logger.debug("Got Rabbit MQ connection actor, requesting subscriber")

  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("plutocore-atomresponder-channel"))
  private implicit val db = dbConfigProvider.get[PostgresProfile].db

  val rmqExchange = config.getOptional[String]("rabbitmq.exchange").getOrElse("pluto-core")

  def channelSetup(channel: Channel, self: ActorRef): Exchange.DeclareOk = {
    channel.exchangeDeclare(rmqExchange, "topic")
  }

  val rmqFactory = new ConnectionFactory()
  rmqFactory.setUri(config.get[String]("rabbitmq.uri"))
  val rmqConnection: ActorRef = system.actorOf(ConnectionActor.props(rmqFactory, reconnectionDelay = 10.seconds), "pluto-core-two")
  val rmqChannel: ActorRef = rmqConnection.createChannel(ChannelActor.props(channelSetup))
  val rmqRouteBase = config.getOptional[String]("rabbitmq.route-base").getOrElse("core")


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

  def publishData(messageType: String, plutoCom: PlutoCommission, commissionId: Int) = {
    val route = s"$rmqRouteBase.commission.${messageType}"

    var messageToSend: String = s"""[{"id":$commissionId}]"""

    if (plutoCom != null) {
      messageToSend = s"""[{"id":${plutoCom.id.get},"created":"${plutoCom.created.toString}","updated":"${plutoCom.updated.toString}","title":"${plutoCom.title}","status":"${plutoCom.status}","description":"${plutoCom.description}","workingGroupId":${plutoCom.workingGroup},"scheduledCompletion":"${plutoCom.scheduledCompletion.toString}","owner":"${plutoCom.owner}","productionOffice":"${plutoCom.productionOffice}"}]"""
    }
    rmqChannel ! ChannelMessage(channel => channel.basicPublish(rmqExchange, route, null, messageToSend.getBytes), dropIfNoChannel = false)
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

      def handleEvent(messageData: MissingCommission): Future[Boolean] = {
        if (messageData.id.isValidInt) {
          logger.debug(s"Missing commission id. is ${messageData.id}")
          val commissionDataObject = getCommission(Some(messageData.id))
          commissionDataObject.onComplete({
            case Success(commissionData) => {
              commissionData match {
                case Some(PlutoCommission(id, collectionId, siteId, created, updated, title, status, description, workingGroup, originalCommissionerName, scheduledCompletion, owner, notes, productionOffice, originalTitle, googleFolder, confidential)) => {
                  logger.debug(s"Found a commission.")
                  publishData("update", commissionData.get, messageData.id)
                }
                case None => {
                  logger.debug(s"No commission found.")
                  publishData("delete", null, messageData.id)
                }
              }
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

    val maybeQueue = Try {
      channel
        .queueDeclare("missing-commissions", false, false, false, null)
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
