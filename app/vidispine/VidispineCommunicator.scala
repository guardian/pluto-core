package vidispine

//import mess.VidispineField;
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, StreamConverters}
import cats.implicits._
import utils.AkkaHttpHelpers
import utils.AkkaHttpHelpers.{RedirectRequired, RetryRequired, consumeStream, contentBodyToJson}
import io.circe.generic.auto._
import org.slf4j.{LoggerFactory, MDC}
import java.net.URLEncoder
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class VidispineCommunicator(config:VidispineConfig) (implicit ec:ExecutionContext, mat:Materializer, actorSystem:ActorSystem){
  private final val logger = LoggerFactory.getLogger(getClass)

  private final val maxFilesToFetch = 10000

  protected def callHttp = Http()

  /**
   * call out to Vidispine and return the content stream if successful. Use this for streaming raw content directly elsewhere
   * @param req HttpRequest to undertake, authorization is added to this
   * @param attempt attempt counter, don't specify this
   * @param retryLimit maximum number of retries
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
          logger.info(s"vidispine redirected to $newUri")
          callToVidispineRaw(req.withUri(newUri), attempt+1, retryLimit)
        case Left(RetryRequired)=>
          if(loggerContext.isDefined) MDC.setContextMap(loggerContext.get)
          Thread.sleep(500*attempt)
          callToVidispineRaw(req, attempt+1, retryLimit)
      })
  }

  /**
   * more conventional `callTo` method which adds an "Accept: application/json" for Vidispine and then attempts
   * to decode the content using Circe to the given domain object. If the parsing fails, then the future will fail too.
   * @param req request to make. Authorization and Accept are both added
   * @param retryLimit maximum number of retries to do before failing
   * @tparam T data type to unmarshal returned JSON into
   * @return a Future, containing the data object or None if a 404 was returned. Other responses return an error.
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

  private def streamingVS(req:HttpRequest, readTimeout:FiniteDuration, thing:String) = callToVidispineRaw(req).map({
    case Some(entity)=>
      entity.dataBytes
        .toMat(StreamConverters.asInputStream(readTimeout))(Keep.right)
        .run()
    case None=>
      throw new RuntimeException(s"$thing does not exist")  //throwing here just causes a failed Future
  })

  /**
   * try to stream the content of the given file.  If the file exists, returns a Future which contains an InputStream
   * that allows the data to be directly streamed into the TransferManager library.
   * If the file does not exist (or another error occurs), then the Future fails
   * @param vsFileId file ID to stream
   * @param readTimeout Optional read timeout for the InputStream. Defaults to 5 seconds
   * @return a Future containing the InputStream
   */
  def streamFileContent(vsFileId:String, readTimeout:FiniteDuration=5.seconds) = {
    val req = HttpRequest(uri = s"${config.baseUri}/API/storage/file/$vsFileId/data")
    streamingVS(req, readTimeout, s"Vidispine file $vsFileId")
  }

  def akkaStreamFileContent(fileId:String) = {
    callToVidispineRaw(HttpRequest(uri = s"${config.baseUri}/API/storage/file/$fileId/data"))
  }

  /**
   * try to stream the XML metadata of the given item.
   * @param itemId
   * @param readTimeout
   * @return
   */
  def streamXMLMetadataDocument(itemId:String, readTimeout:FiniteDuration=5.seconds) = {
    val headers = Seq(Accept(MediaRange(MediaTypes.`application/xml`)))
    val req = HttpRequest(uri = s"${config.baseUri}/API/item/$itemId/metadata", headers = headers)
    streamingVS(req, readTimeout, s"Vidispine item $itemId")
  }

  def akkaStreamXMLMetadataDocument(itemId:String, readTimeout:FiniteDuration=5.seconds) = {
    val headers = Seq(Accept(MediaRange(MediaTypes.`application/xml`)))
    val req = HttpRequest(uri = s"${config.baseUri}/API/item/$itemId/metadata", headers = headers)
    callToVidispineRaw(req)
  }

  def getResourceUriList(itemId:String, itemVersion:Option[Int], resourceType: VidispineCommunicator.ResourceType.Value) = {
    val baseUriString = resourceType match {
      case VidispineCommunicator.ResourceType.Poster => s"${config.baseUri}/API/item/$itemId/posterresource"
      case VidispineCommunicator.ResourceType.Thumbnail => s"${config.baseUri}/API/item/$itemId/thumbnailresource"
    }
    val req = itemVersion match {
      case None=>HttpRequest(uri = baseUriString)
      case Some(version)=>HttpRequest(uri = baseUriString + s"?version=$version")
    }
    callToVidispine[OptionalUriListDocument](req).map(_.flatMap(_.toOption))
  }

  protected def getThumbnailsList(maybeResourceUri:Option[String]) = maybeResourceUri.map(thumbnailResourceUri=>
      callToVidispine[OptionalUriListDocument](HttpRequest(uri = thumbnailResourceUri)).map(_.flatMap(_.toOption))
  ).sequence.map(_.flatten)

  protected def findFirstThumbnail(maybeResourceUri:Option[String], maybeThumbnailsList:Option[UriListDocument]) = {
    (maybeResourceUri, maybeThumbnailsList.flatMap(_.uri.headOption)) match {
      case (Some(resourceUri), Some(firstEntry))=>
        logger.debug(s"resource uri $resourceUri, first entry is $firstEntry")
        val targetUri = s"$resourceUri/$firstEntry"
        callToVidispineRaw(HttpRequest(uri=targetUri))
      case (_, _)=>Future(None)
    }
  }

  def akkaStreamFirstThumbnail(itemId:String, itemVersion:Option[Int]) = {
    for {
      maybeResourceUri <- getResourceUriList(itemId, itemVersion, VidispineCommunicator.ResourceType.Thumbnail).map(_.flatMap(_.uri.headOption))
      maybeThumbnailsList <- getThumbnailsList(maybeResourceUri)
      maybeStream <- findFirstThumbnail(maybeResourceUri, maybeThumbnailsList)
    } yield maybeStream
  }

  /**
   * tries to get an InputStream to obtain the data for the first thumbnail for the given item
   * If there is no data None is returned, otherwise an InputStream is returned
   * @param itemId
   * @param itemVersion
   * @return
   */
  def streamFirstThumbnail(itemId:String, itemVersion:Option[Int], readTimeout:FiniteDuration=5.seconds) = akkaStreamFirstThumbnail(itemId, itemVersion)
    .map(
      _.map(
        _.dataBytes.toMat(StreamConverters.asInputStream(readTimeout))(Keep.right).run()
      )
    )

  /**
   * tries to get an InputStream to obtain the data for the poster image for the given item.
   * If there is no data None is returned, otherwise InputStream is returned
   * @param itemId the item to get
   * @param itemVersion optional item version. Defaults to the latest one.
   * @param readTimeout optional read timeout for the InputStream. Defaults to 5 seconds
   * @return a Future, which contains either a connected InputStream or None.
   */
  def streamPosterForItem(itemId:String, itemVersion:Option[Int], readTimeout:FiniteDuration=5.seconds) = {
    getResourceUriList(itemId, itemVersion, VidispineCommunicator.ResourceType.Poster).flatMap({
      case None=>Future(None)
      case Some(uriList)=>
        val req = HttpRequest(uri = uriList.uri.head)
        streamingVS(req, readTimeout, s"Poster for vidispine item $itemId").map(Some.apply)
    })
  }

  def getFileInformation(fileId:String) = {
    callToVidispine[FileDocument](HttpRequest(uri=s"${config.baseUri}/API/storage/file/$fileId"))
  }

  /**
   * Looks up the given shape ID on the given item ID and returns a ShapeDocument
   * @param itemId item ID to look up
   * @param shapeId shape ID to look up, on the given item
   * @return a failed future on error, a Future(None) if nothing was found or a Future with a ShapeDocument if it was found
   */
  def findItemShape(itemId:String, shapeId:String) = {
    callToVidispine[ShapeDocument](HttpRequest(uri = s"${config.baseUri}/API/item/$itemId/shape/$shapeId"))
  }

  def setMetadataValue(itemId:String, field:String, value:String) = {
    import io.circe.generic.auto._
    import io.circe.syntax._
    val doc = MetadataWrite.simpleKeyValue(field, value)
    callToVidispine[ItemResponseSimplified](HttpRequest(
      uri = s"${config.baseUri}/API/item/$itemId/metadata",
      method = HttpMethods.PUT,
      entity = HttpEntity(ContentTypes.`application/json`, doc.asJson.noSpaces)
    ))
  }

  def setGroupedMetadataValue(itemId:String, groupName:String, field:String, value:String) = {
    import io.circe.generic.auto._
    import io.circe.syntax._
    val doc = MetadataWrite.groupedKeyValue(groupName, field, value)
    callToVidispine[ItemResponseSimplified](HttpRequest(
      uri = s"${config.baseUri}/API/item/$itemId/metadata",
      method = HttpMethods.PUT,
      entity = HttpEntity(ContentTypes.`application/json`, doc.asJson.noSpaces)
    ))
  }

  def searchByPath(pathToFind:String) = {
    import io.circe.generic.auto._

    val encodedPath = URLEncoder.encode(pathToFind, "UTF-8")
    callToVidispine[FileListDocument](HttpRequest(
      uri = s"${config.baseUri}/API/storage/file?path=${encodedPath}&includeItem=true",
      method = HttpMethods.GET
    ))
  }

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
          logger.info(s"Vidispine Output: $searchResultDocument")
          searchResultDocument.entry.map(simplifiedItem => VSOnlineOutputMessage.fromResponseItem(simplifiedItem, projectId))
        case None => Seq[Option[VSOnlineOutputMessage]]()
      })
  }


  /**
   * returns a sequence of ShapeDocuments for every shape on the given item.
   *
   * @param itemId the item ID to query
   * @return A Future containing - None if the item was not found, or a (possibly empty) sequence of ShapeDocuments
   *         representing the individual shapes
   */
  def listItemShapes(itemId:String) = {
    for {
      shapeIdList <- callToVidispine[ShapeListDocument](HttpRequest(uri = s"${config.baseUri}/API/item/$itemId/shape"))
      result <- shapeIdList match {
        case Some(shapeDoc)=>
          shapeDoc.uri match {
            case Some(uriList) =>
              Future
                .sequence(uriList.map(shapeId => findItemShape(itemId, shapeId)))
                .map(_.collect({ case Some(shapeDoc) => shapeDoc }))
                .map(Some(_))
            case None =>
              Future(Some(Seq())) //if there are no shapes, then the `uri` parameter is empty
          }
        case None=>
          Future(None)
      }
    } yield result
  }

  /**
  Tries to find the file associated with the given shape tag on the given item
   @param itemId - the item ID to query
   @param shapeTag - name of the shape tag we are interested in
   @return a Future containing None if the item, shape or file was not found or a VSShapeFile describing the file if it was present
   */
  def findItemFile(itemId:String, shapeTag:String) = {
    listItemShapes(itemId).map({
      case None=>None
      case Some(shapes)=>
        val maybeWantedShape = shapes.find(_.tag.contains(shapeTag))
        maybeWantedShape.flatMap(_.getLikelyFile)
    })
  }

  def getMetadata(itemId:String) = {
    val req = HttpRequest(HttpMethods.GET, uri=s"${config.baseUri}/API/item/$itemId/metadata", headers = Seq(
      Accept(MediaRange(MediaTypes.`application/json`))
    ))
    callToVidispine[ItemResponseSimplified](req)
  }
}

object VidispineCommunicator {
  object ResourceType extends Enumeration {
    val Poster, Thumbnail = Value
  }
}
