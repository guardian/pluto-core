package services

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq._
import com.rabbitmq.client.{AMQP, ShutdownSignalException}
import models.{StorageEntry, StorageEntryHelper}
import org.slf4j.LoggerFactory
import play.api.Configuration

import javax.inject.{Inject, Named, Singleton}
import scala.jdk.CollectionConverters._
import scala.util.Try
import org.apache.commons.codec.binary.StringUtils

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PeriodicScanReceiver @Inject() (config:Configuration,
                                      projectBackup: ProjectBackup,
                                      @Named("postrun-action-scanner") postrunActionScanner:ActorRef,
                                      @Named("storage-scanner") storageScanner: ActorRef,
                                      @Named("commission-status-propagator") commissionStatusPropagator: ActorRef
                                     )(implicit system:ActorSystem) {
  import io.circe.generic.auto._
  import ServiceEventCodec._

  private val logger = LoggerFactory.getLogger(getClass)
  private val factory = new ConnectionFactory()
  logger.debug("got rmq connection factory")
  factory.setUri(config.get[String]("rabbitmq.uri"))
  val exchangeName = config.getOptional[String]("rabbitmq.exchange").getOrElse("pluto-core")

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
            handleScanEvent(consumerTag, envelope.getRoutingKey, scanEvent).map({
              case true=>
                channel.basicAck(envelope.getDeliveryTag, false)
              case false=>
                logger.warn("Could not handle message, leaving it on-queue")
            })
        }
      }

      def handleScanEvent(consumerTag: String, routingKey: String, scanEvent: ScanEvent): Future[Boolean] = {
        scanEvent.action match {
          case ServiceEventAction.CancelAction=>
            logger.warn(s"Received cancel for '$routingKey' with '$consumerTag' but this is not implemented yet")
            Future(false)
          case ServiceEventAction.PerformAction=>
            routingKey match {
              case "pluto.core.service.storagescan"=>
                logger.info("Triggering storage check in response to incoming message")
                storageScanner ! StorageScanner.Rescan
                Future(true)
              case "pluto.core.service.commissionstatuspropagator"=>
                logger.info("Triggering commission status propagator check for retries in response to incoming message")
                commissionStatusPropagator ! CommissionStatusPropagator.RetryFromState(UUID.randomUUID())
                Future(true)
              case "pluto.core.service.postrunaction"=>
                logger.info("Triggering postrun action check in response to incoming message")
                postrunActionScanner ! PostrunActionScanner.Rescan
                Future(true)
              case "pluto.core.service.backuptrigger"=>
                logger.info("Triggering backup action in response to incoming message")
                projectBackup.backupProjects
                  .map(results=>{
                    logger.info(s"Timed backup completed.  ${results.length} storages were backed up")
                    results.foreach(entry=>{
                      logger.info(s"\tStorage ID ${entry.storageId}: ${entry.totalCount} files total, ${entry.successCount} copied, ${entry.notNeededCount} did not need backup and ${entry.failedCount} failed")
                    })
                    true
                  })
                  .recover({
                    case err:Throwable=>
                      logger.error(s"Timed backup failed: ${err.getMessage}", err)
                      false
                  })
              case _=>
                logger.warn(s"PeriodicScanReceiver got an unknown message: $routingKey")
                Future(true)
            }
        }
      }

      override def handleRecoverOk(consumerTag: String): Unit = super.handleRecoverOk(consumerTag)
    }
  }

  def setupSubscriber(channel:Channel,  self:ActorRef) = {
    logger.debug(s"setting up rmq subscriber")

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
