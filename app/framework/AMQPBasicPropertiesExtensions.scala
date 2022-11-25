package framework

import com.rabbitmq.client.AMQP

import scala.jdk.CollectionConverters._
import scala.util.Try

object AMQPBasicPropertiesExtensions {
  implicit class EnhancedBasicProperties(p:AMQP.BasicProperties) {
    /**
     * scala-centric way of getting a value from the BasicProperties headers.
     * Safely handles the case where p.getHeaders returns null
     * @param key key that you want
     * @tparam T data type of key
     * @return None if the key is not present or is of the wrong data type, or the value
     */
    def getHeader[T](key:String) = Option(p.getHeaders)
      .flatMap(_.asScala.get(key))
      .flatMap(value=>Try{ value.asInstanceOf[T]}.toOption)

    /**
     * does the same thing as getHeader but does not drop the error message if type conversion fails, instead returning
     * it as the Left portion of an Either
     * @param key key that you want
     * @tparam T data type of key
     * @return Right(None) if the key is not present, Left with an error string if it is the wrong data type or Right(Some(value)) if successful
     */
    def getHeaderFull[T](key:String) = Option(p.getHeaders)
      .flatMap(_.asScala.get(key)) match {
      case None=>Right(None)
      case Some(value)=>
        Try { Some(value.asInstanceOf[T]) }.toEither.left.map(_.getMessage)
    }
  }
}
