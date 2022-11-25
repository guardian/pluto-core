package utils

import akka.http.scaladsl.model.{HttpEntity, HttpResponse, ResponseEntity, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

/**
 * this object can be imported into communicator classes, and supplies routines to buffer the response entity into
 * memory and to parse/unmarshal it as JSON
 */
object AkkaHttpHelpers {
  private val logger = LoggerFactory.getLogger(getClass)

  sealed trait HttpUnsuccessActions
  case class RedirectRequired(to:Uri) extends HttpUnsuccessActions
  case object RetryRequired extends HttpUnsuccessActions

  /**
   * internal method that consumes a given response entity to a String
   *
   * @param entity ResponseEntity object
   * @return a Future containing the String of the content
   */
  def consumeResponseEntity(entity: ResponseEntity)(implicit mat:Materializer, ec:ExecutionContext) = {
    val sink = Sink.reduce[ByteString]((acc, elem) => acc ++ elem)
    entity.dataBytes.toMat(sink)(Keep.right).run().map(_.utf8String)
  }

  def  consumeStream(stream:Source[ByteString, Any])(implicit mat:Materializer, ec:ExecutionContext) = {
    val sink = Sink.reduce[ByteString]((acc, elem) => acc ++ elem)
    stream.toMat(sink)(Keep.right).run().map(_.utf8String)
  }
  /**
   * convenience method that consumes a given response entity and parses it into a Json object
   *
   * @param entity ResponseEntity object
   * @return a Future containing either the ParsingFailure error or the parsed Json object
   */
  def consumeResponseEntityJson(entity: ResponseEntity)(implicit mat:Materializer, ec:ExecutionContext) = consumeResponseEntity(entity)
    .map(io.circe.parser.parse)

  def contentBodyToJson[T: io.circe.Decoder](contentBody: Future[String])(implicit mat:Materializer, ec:ExecutionContext) = contentBody
    .map(io.circe.parser.parse)
    .map(_.map(json => (json \\ "result").headOption.getOrElse(json))) //if the actual object is hidden under a "result" field take that
    .map(_.flatMap(_.as[T]))
    .map({
      case Left(err) =>
        logger.error(s"Problematic response: ${contentBody.value}")
        throw new RuntimeException("Could not understand server response: ", err)
      case Right(data) => Some(data)
    })

  /**
   * handle the HTTP response from akka.  This will return different actions to take depending on the response code, all returned in a Future:
   * - 200 => returns a Right, containing a ByteString source of the response body all contained in a Future
   * - 404 => returns a Right, containing None
   * - 403/401 => returns a failed Future with an error message
   * - 400 => returns a failed Future with an error message
   * - 301 => retrieves the Location header from the response and returns a Left with a RedirectRequired value giving the location to redirect to
   * - 50x => returns a Left with a RetryRequired value
   * @param response the HttpResponse to process
   * @param description descriptive string of the remote component being talked to, for logging
   * @param mat implicitly provided Materializer
   * @param ec implicitly provided ExecutionContext
   * @return a Future indicating the action to take.
   */
  def handleResponse(response:HttpResponse, description:String) (implicit mat:Materializer, ec:ExecutionContext):Future[Either[HttpUnsuccessActions, Option[HttpEntity]]] = response.status.intValue() match {
    case 200 =>
      Future(Right(Some(response.entity)))
    case 404 =>
      response.entity.discardBytes()
      Future(Right(None))
    case 403|401 =>
      response.entity.discardBytes()
      Future.failed(new RuntimeException(s"$description said permission denied."))
    case 409 =>
      consumeResponseEntity(response.entity)
        .flatMap(body => Future.failed(new RuntimeException(s"$description returned a conflict error: $body")))
    case 400 =>
      consumeResponseEntity(response.entity)
        .flatMap(body => Future.failed(new RuntimeException(s"$description returned bad data error: $body")))
    case 301 |302|303|308|309=>
      response.entity.discardBytes()
      logger.info(s"Received unexpected redirect from $description to ${response.getHeader("Location")}")
      val h = response.getHeader("Location")
      if (h.isPresent) {
        Future(Left(RedirectRequired(h.get().value())))
      } else {
        Future.failed(new RuntimeException(s"$description returned an Unexpected redirect without location"))
      }
    case 500 | 502 | 503 | 504 =>
      consumeResponseEntity(response.entity).map(body=>{
        logger.error(s"$description returned a server error ${response.status}: $body. Retrying...")
        Left(RetryRequired)
      })
    case _=>
      consumeResponseEntity(response.entity)
        .flatMap(body=>{
          logger.error(s"Received unexpected response ${response.status} from $description, with content $body")
          Future.failed(new RuntimeException(s"$description returned unexpected response: ${response.status}"))
        })
  }
}
