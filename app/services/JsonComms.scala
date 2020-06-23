package services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpResponse, headers}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsValue, Json}
import org.slf4j.MDC

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

trait JsonComms {
  protected val logger:Logger
  implicit val actorSystem:ActorSystem
  implicit val materializer:Materializer

  implicit val configuration:Configuration

  protected def getPlutoAuth = headers.Authorization(BasicHttpCredentials(configuration.get[String]("pluto.username"),configuration.get[String]("pluto.password")))

  /**
    * generic response handler that takes the HttpResponse object as returned from connection and converts to an Either
    * indicating success or failure.  On failure (i.e., a non-200 response code), a Left[Boolean] is returned with the
    * parameter indicating whether to retry or not, on success a parsed JsValue is returned
    * @param response `HttpResponse` object returend from the server
    * @param ec implicitly provided execution context
    * @return Either[Boolean,JsValue]
    */
  protected def handlePlutoResponse(response:HttpResponse)(implicit ec:ExecutionContext):Either[Boolean,JsValue] = {
    val body = Await.result(bodyAsJsonFuture(response), 5.seconds)

    MDC.put("http_status",response.status.toString())
    MDC.put("response_body", body.toString)

    if (response.status.intValue()>=200 && response.status.intValue() <= 299) {
      logger.info("Pluto responded success")
      body match {
        case Left(unparsed)=>
          logger.warn("Could not parse server response, full response is shown as DEBUG message")
          logger.debug(unparsed)
          Left(true)
        case Right(parsed)=>Right(parsed)
      }

    } else if (response.status.intValue()>=500 && response.status.intValue()<=599) {
      logger.error("Unable to update pluto, server returned 50x error.")
      body match {
        case Left(unparsed) =>
          logger.warn("Could not parse server response, full response is shown as DEBUG message")
          logger.debug(unparsed)
        case Right(parsed) =>
          logger.info(parsed.toString())
      }
      Left(true) //should retry
    } else if (response.status.intValue()>=400 && response.status.intValue() <=499){
      logger.error("Unable to update pluto, server returned bad data (40x) error.")
      body match {
        case Left(unparsed) =>
          logger.warn("Could not parse server response, full response is shown as DEBUG message")
          logger.debug(unparsed)
        case Right(parsed) =>
          logger.info(parsed.toString())
      }
      Left(false) //should not retry
    } else {
      logger.error(s"Unable to update pluto, server returned ${response.status}. Not retrying.")
      Left(false)
    }
  }


  protected def bodyAsJsonFuture(response:HttpResponse)(implicit ec:ExecutionContext, materializer:Materializer):Future[Either[String, JsValue]] = {
    val sink = Sink.fold[String, ByteString]("")(_ + _.decodeString("UTF-8"))

    response.entity.dataBytes.runWith(sink)
      .map(dataString=>
        try {
          Right(Json.parse(dataString))
        } catch {
          case error:Throwable=>
            logger.error(s"Could not decode json object: ", error)
            Left(dataString)
        }
      )
  }
}
