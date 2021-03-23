package services

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq._
import com.rabbitmq.client.{AMQP, ShutdownSignalException}
import org.slf4j.LoggerFactory
import play.api.Configuration

import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters._

@Singleton
class PeriodicScanReceiver @Inject() (config:Configuration)(implicit system:ActorSystem) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val factory = new ConnectionFactory()
  logger.debug("got rmq connection factory")

  //get a connection actor
  private val connection = system.actorOf(ConnectionActor.props(factory), "scan-receiver-connection")
  logger.debug("got rmq connection actor, requesting subscriber")
  //configure ourselves as a subscriber
  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("plutocore-serviceevents"))

  def makeConsumer(channel:Channel) = {
    new DefaultConsumer(channel) {
      //note - docs say to avoid long-running code here because it delays dispatch of other messages on the same connection
      override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = super.handleShutdownSignal(consumerTag, sig)

      override def handleCancel(consumerTag: String): Unit = super.handleCancel(consumerTag)

      override def handleCancelOk(consumerTag: String): Unit = super.handleCancelOk(consumerTag)

      override def handleConsumeOk(consumerTag: String): Unit = super.handleConsumeOk(consumerTag)

      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit = super.handleDelivery(consumerTag, envelope, properties, body)

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
