package services.migrationcomponents

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharset, HttpEntity, HttpMethod, HttpMethods, HttpRequest, HttpResponse, MediaRange, MediaTypes}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.http.scaladsl.server.ContentNegotiator.Alternative.MediaType
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

class VSProjectSource (vsBaseUri:String, vsUser:String, vsPasswd: String, pageSize:Int=10, gnmType:String="project")
                      (implicit actorSystem: ActorSystem, mat:Materializer)
  extends VSContainerSource[VSProjectEntity](vsBaseUri, vsUser, vsPasswd, pageSize, gnmType) {

  override def listConverter(entry:JsValue) = VSProjectEntity.fromList(entry)
}

class VSContainerSource[T] (vsBaseUri:String, vsUser:String, vsPasswd: String, pageSize:Int=10, gnmType:String="project")(implicit actorSystem: ActorSystem, mat:Materializer) extends GraphStage[SourceShape[T]] {
  private final val logger = LoggerFactory.getLogger(getClass)
  private final val out:Outlet[T] = Outlet.create("VSProjectSource.out")
  private implicit val ec:ExecutionContext = actorSystem.dispatcher

  override def shape: SourceShape[T] = SourceShape.of(out)

  def listConverter(entry:JsValue):scala.collection.IndexedSeq[T]

  /**
    * isolate the Http request so we can mock it in testing
    * @param req HttpRequest to make
    * @return a Future containing the HttpResponse
    */
  def makeHttpRequest(req:HttpRequest) = Http().singleRequest(req)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var cachedItems:Seq[T] = Seq()
    private var lastIndex=1

    /**
      * reads in and buffers the response body
      * @param response
      * @return
      */
    private def consumeBody(response:HttpResponse):Future[ByteString] = {
      response.entity.dataBytes.toMat(Sink.reduce[ByteString]((acc, elem)=>acc.concat(elem)))(Keep.right).run()
    }

    def getNextPage():Future[Either[Int, Option[T]]] = {
      val uri = s"$vsBaseUri/API/collection;first=$lastIndex;number=$pageSize?content=metadata&count=false"
      logger.debug(s"URI is $uri")
      val requestXml = <ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <field>
          <name>gnm_type</name>
          <value>{ gnmType }</value>
        </field>
      </ItemSearchDocument>
      logger.debug(s"requestXml is ${requestXml.toString()}")

      val auth = Authorization(BasicHttpCredentials(vsUser, vsPasswd))
      val accept = Accept(MediaRange(MediaTypes.`application/json`))

      val req = HttpRequest(HttpMethods.PUT, uri, Seq(auth, accept)).withEntity(ContentType.WithCharset(MediaTypes.`application/xml`, HttpCharset("UTF-8")(Seq())), requestXml.toString())
      makeHttpRequest(req).flatMap(response=>{
        if(response.status==StatusCodes.BAD_GATEWAY || response.status==StatusCodes.GATEWAY_TIMEOUT) {
          response.entity.discardBytes()
          logger.warn("Vidispine timed out when accessing, retrying...")
          Thread.sleep(2000)
          getNextPage()
        } else {
          consumeBody(response).map(serverBytes => {
            if (response.status != StatusCodes.OK) {
              logger.warn(s"Could not load items from Vidispine: ${serverBytes.utf8String}")
              Left(response.status.intValue())
            } else {
              val serverJson = Json.parse(serverBytes.toArray)
              logger.debug(serverJson.toString())

              val newProjects = (serverJson \ "collection").asOpt[JsValue].map(listConverter).getOrElse(IndexedSeq())
              if(newProjects.nonEmpty) {
                this.synchronized {
                  cachedItems = cachedItems ++ newProjects.tail
                  lastIndex += newProjects.length
                }
              }
              Right(newProjects.headOption)
            }
          })
        }
      })
    }

    val nextItemCb = createAsyncCallback[T](item=>push(out, item))
    val completedCb = createAsyncCallback[Unit](_=>complete(out))
    val errCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        this.synchronized {
          cachedItems.headOption match {
            case Some(nextItem)=>
              cachedItems = cachedItems.tail
              nextItemCb.invoke(nextItem)
              return
            case None=>
          }
        }

        //we only get here if there are no items left in the list
        getNextPage().map({
          case Left(_)=>
            errCb.invoke(new RuntimeException("Could not communicate with Vidispine"))
          case Right(None)=>
            completedCb.invoke( () )
          case Right(Some(nextItem))=>
            nextItemCb.invoke(nextItem)
        }).recover({
          case err:Throwable=>
            logger.error("Vidispine request crashed: ", err)
            errCb.invoke(err)
        })
      }
    })
  }
}
