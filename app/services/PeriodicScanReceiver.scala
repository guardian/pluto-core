package services

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq._
import com.rabbitmq.client.{AMQP, ShutdownSignalException}
import org.slf4j.LoggerFactory
import play.api.Configuration

import javax.inject.{Inject, Named, Singleton}
import scala.jdk.CollectionConverters._
import scala.util.Try
import org.apache.commons.codec.binary.StringUtils

import java.util.UUID

@Singleton
class PeriodicScanReceiver @Inject() (config:Configuration,
                                      @Named("postrun-action-scanner") postrunActionScanner:ActorRef,
                                      @Named("storage-scanner") storageScanner: ActorRef,
                                      @Named("commission-status-propagator") commissionStatusPropagator: ActorRef
                                     )(implicit system:ActorSystem) {
  import io.circe.generic.auto._
  import ServiceEventCodec._

  private val logger = LoggerFactory.getLogger(getClass)
  private val factory = new ConnectionFactory()
  logger.debug("got rmq connection factory")

  //get a connection actor
  private val connection = system.actorOf(ConnectionActor.props(factory), "scan-receiver-connection")
  logger.debug("got rmq connection actor, requesting subscriber")
  //configure ourselves as a subscriber
  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("plutocore-serviceevents"))

  def loadScanEvent(body:Array[Byte]) = {
    for {
      bodyAsString <- Try { StringUtils.newStringUtf8(body)}.toEither
      parsedContent <- io.circe.parser.parse(bodyAsString)
      marshalledContent <- parsedContent.as[ScanEvent]
    } yield marshalledContent
  }

  def makeConsumer(channel:Channel) = {
    new DefaultConsumer(channel) {
      //note - docs say to avoid long-running code here because it delays dispatch of other messages on the same connection
      override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = super.handleShutdownSignal(consumerTag, sig)

      override def handleCancel(consumerTag: String): Unit = super.handleCancel(consumerTag)

      override def handleCancelOk(consumerTag: String): Unit = super.handleCancelOk(consumerTag)

      override def handleConsumeOk(consumerTag: String): Unit = super.handleConsumeOk(consumerTag)

      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit = {
        loadScanEvent(body) match {
          case Left(err)=>
            logger.error(s"Received invalid scan request with key ${envelope.getRoutingKey} from exchange ${envelope.getExchange}: ${err.getMessage}", err)

          case Right(scanEvent)=>
            if(handleScanEvent(consumerTag, envelope.getRoutingKey, scanEvent)) {
              channel.basicAck(envelope.getDeliveryTag, false)
            } else {
              logger.warn("Could not handle message, leaving it on-queue")
            }
        }
      }

      def handleScanEvent(consumerTag: String, routingKey: String, scanEvent: ScanEvent): Boolean = {
        scanEvent.action match {
          case ServiceEventAction.CancelAction=>
            logger.warn(s"Received cancel for '$routingKey' with '$consumerTag' but this is not implemented yet")
            false
          case ServiceEventAction.PerformAction=>
            routingKey match {
              case "pluto.core.service.storagescan"=>
                logger.info("Triggering storage scan in response to incoming message")
                storageScanner ! StorageScanner.Rescan
                true
              case "pluto.core.service.commissionstatuspropagator"=>
                logger.info("Triggering commission status propagator regular retry in response to incoming message")
                commissionStatusPropagator ! CommissionStatusPropagator.RetryFromState(UUID.randomUUID())
                true
              case "pluto.core.service.postrunaction"=>
                logger.info("Triggering postrun action scanner in response to incoming message")
                postrunActionScanner ! PostrunActionScanner.Rescan
                true
              case _=>
                logger.warn(s"PeriodicScanReceiver got an unknown message: $routingKey")
                true
            }
        }
      }

      override def handleRecoverOk(consumerTag: String): Unit = super.handleRecoverOk(consumerTag)
    }
  }

  def setupSubscriber(channel:Channel,  self:ActorRef) = {
    logger.debug(s"setting up rmq subscriber")
    val exchangeName = config.getOptional[String]("rabbitmq.own-exchange").getOrElse("pluto-core")
    logger.debug(s"exchange name is $exchangeName")

    val queue = channel
      .queueDeclare("service-actions-received",true, false, false, Map[String,AnyRef]().asJava)
      .getQueue
    logger.debug("binding to exchange....")
    channel.queueBind(queue, exchangeName, "pluto.core.service.#")
    logger.debug("initiating consumer...")
    channel.basicConsume(queue, makeConsumer(channel))
    logger.debug("setup done")
  }

}
