package vidispine

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import utils.AkkaHttpHelpers
import utils.AkkaHttpHelpers.{RedirectRequired, RetryRequired, consumeStream, contentBodyToJson}
import org.slf4j.{LoggerFactory, MDC}
import scala.concurrent.{ExecutionContext, Future}


class VidispineCommunicator(config:VidispineConfig) (implicit ec:ExecutionContext, mat:Materializer, actorSystem:ActorSystem){
  private final val logger = LoggerFactory.getLogger(getClass)

  private final val maxFilesToFetch = 10000

  protected def callHttp = Http()

  /**
   * Call out to Vidispine and return the content stream if successful. Use this for streaming raw content directly elsewhere
   * @param req HttpRequest to undertake, authorization is added to this
   * @param attempt Attempt counter, don't specify this
   * @param retryLimit Maximum number of retries
   * @return
   */
  protected def callToVidispineRaw(req: HttpRequest, attempt: Int = 1, retryLimit:Int=10):Future[Option[HttpEntity]] = if (attempt > retryLimit) {
    Future.failed(new RuntimeException("Too many retries, see logs for details"))
  } else {
    logger.debug(s"Vidispine request URL is ${req.uri.toString()}")

    val updatedReq = req.withHeaders(req.headers ++ Seq(Authorization(BasicHttpCredentials(config.username, config.password))))

    val loggerContext = Option(MDC.getCopyOfContextMap)

    callHttp
      .singleRequest(updatedReq)
      .flatMap(response=>{
        if(loggerContext.isDefined) MDC.setContextMap(loggerContext.get)
        AkkaHttpHelpers.handleResponse(response,"Vidispine")
      })
      .flatMap({
        case Right(Some(entity))=>
          if(loggerContext.isDefined) MDC.setContextMap(loggerContext.get)
          Future(Some(entity))
        case Right(None)=>
          if(loggerContext.isDefined) MDC.setContextMap(loggerContext.get)
          Future(None)
        case Left(RedirectRequired(newUri))=>
          if(loggerContext.isDefined) MDC.setContextMap(loggerContext.get)
          logger.info(s"Vidispine redirected to $newUri")
          callToVidispineRaw(req.withUri(newUri), attempt+1, retryLimit)
        case Left(RetryRequired)=>
          if(loggerContext.isDefined) MDC.setContextMap(loggerContext.get)
          Thread.sleep(500*attempt)
          callToVidispineRaw(req, attempt+1, retryLimit)
      })
  }

  /**
   * More conventional `callTo` method which adds an "Accept: application/json" for Vidispine and then attempts
   * to decode the content using Circe to the given domain object. If the parsing fails, then the future will fail too.
   * @param req Request to make. Authorization and Accept are both added
   * @param retryLimit Maximum number of retries to do before failing
   * @tparam T Data type to unmarshal returned JSON into
   * @return A Future, containing the data object or None if a 404 was returned. Other responses return an error.
   */
  protected def callToVidispine[T:io.circe.Decoder](req: HttpRequest, retryLimit:Int=10):Future[Option[T]] =
    callToVidispineRaw(
      req.withHeaders(req.headers :+ Accept(MediaRange(MediaTypes.`application/json`))),
      retryLimit = retryLimit
    ).flatMap({
      case None => Future(None)
      case Some(entity) =>
        logger.debug(s"Vidispine URL was ${req.uri} with headers ${req.headers}")
        contentBodyToJson(consumeStream(entity.dataBytes))
    })

  def getFilesOfProject(projectId: Int, pageSize: Int = 100): Future[Seq[VSOnlineOutputMessage]] = {
      recursivelyGetFilesOfProject(projectId, pageSize = pageSize).map(_.collect({case Some(t) => t}))
  }

  def recursivelyGetFilesOfProject(projectId: Int, start: Int = 1, pageSize: Int = 100, existingResults: Seq[Option[VSOnlineOutputMessage]] = Seq()): Future[Seq[Option[VSOnlineOutputMessage]]] = {
    getPageOfFilesOfProject(projectId, start, pageSize).flatMap(results => {
      if (results.isEmpty || start > maxFilesToFetch) {
        if (start > maxFilesToFetch) logger.warn(s"Exiting early from getting online files, because we have found more than maxFilesToFetch: $maxFilesToFetch, namely ${existingResults.length + results.length} files")
        Future(existingResults ++ results)
      } else {
        recursivelyGetFilesOfProject(projectId, start + results.size, pageSize, existingResults ++ results)
      }
    })
  }

  def getPageOfFilesOfProject(projectId:Int, currentItem:Int = 1, pageSize: Int = 100): Future[Seq[Option[VSOnlineOutputMessage]]] = {
    import io.circe.generic.auto._

    val doc =
      <ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <field>
          <name>gnm_containing_projects</name>
          <value>{projectId}</value>
        </field>
        <intervals>generic</intervals>
      </ItemSearchDocument>

    val searchResult = callToVidispine[SearchResultDocument](
      HttpRequest(
        uri = s"${config.baseUri}/API/search;first=$currentItem;number=$pageSize?content=shape,metadata&tag=original&field=title,gnm_category,gnm_containing_projects,gnm_nearline_id,itemId",
        method = HttpMethods.PUT,
        entity = HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), doc.toString)))

      searchResult.map({
        case Some(searchResultDocument) =>
          searchResultDocument.entry.map(simplifiedItem => VSOnlineOutputMessage.fromResponseItem(simplifiedItem, projectId))
        case None => Seq[Option[VSOnlineOutputMessage]]()
      })
  }
}

object VidispineCommunicator {
  object ResourceType extends Enumeration {
    val Poster, Thumbnail = Value
  }
}
