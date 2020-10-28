package services.migrationcomponents

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, MediaRange, MediaTypes}
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

case class VSGlobalMetadataEntry(uuid:UUID, entries:Map[String,String])

case class VSGlobalMetadataGroup(name:String, entries:Seq[VSGlobalMetadataEntry]) {
  def valueFor(uuid:UUID):Option[VSGlobalMetadataEntry] = entries.find(_.uuid == uuid)
}

class VSGlobalMetadata(implicit actorSystem: ActorSystem, mat:Materializer, ec:ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)
  /**
    * isolate the Http request so we can mock it in testing
    * @param req HttpRequest to make
    * @return a Future containing the HttpResponse
    */
  def makeHttpRequest(req:HttpRequest) = Http().singleRequest(req)

  /**
    * reads in and buffers the response body
    * @param response
    * @return
    */
  private def consumeBody(response:HttpResponse):Future[ByteString] = {
    response.entity.dataBytes.toMat(Sink.reduce[ByteString]((acc, elem)=>acc.concat(elem)))(Keep.right).run()
  }

  def extractGlobalMetadata(vsBaseUri:String, vsUser:String, vsPasswd:String):Future[JsValue] = {
    val uri = s"$vsBaseUri/API/metadata"
    val auth = Authorization(BasicHttpCredentials(vsUser, vsPasswd))
    val accept = Accept(MediaRange(MediaTypes.`application/json`))

    val req = HttpRequest(HttpMethods.PUT, uri, Seq(auth, accept))

    makeHttpRequest(req).flatMap(response=>{
      if(response.status==StatusCodes.BAD_GATEWAY || response.status==StatusCodes.GATEWAY_TIMEOUT) {
        response.entity.discardBytes()
        Future.failed(new RuntimeException("Vidispine timed out"))
      } else {
        consumeBody(response).map(serverBytes => {
          if (response.status != StatusCodes.OK) {
            logger.warn(s"Could not load global meta from Vidispine: ${serverBytes.utf8String}")
            throw new RuntimeException("Server error")
          } else {
            Json.parse(serverBytes.toArray)
          }
        })
      }
    })
  }

  /**
    * converts the key/value content for the given group entry to a VSGlobalMetadataEntry
    * @param from
    * @return
    */
  def groupContent(uuidStr:String, from:JsArray) = {
    val uuid = UUID.fromString(uuidStr)
    val rawKv = for {
      field <- from.value
      valueStruct <- (field \ "value").as[JsArray].value
    } yield ((field \ "name").as[String], (valueStruct \ "value").as[String])

    VSGlobalMetadataEntry(uuid, rawKv.toMap)
  }

  def groupForName(name:String, groups:IndexedSeq[JsValue]) = {
    val rawGroupEntries = for {
      groupEntry <- groups if (groupEntry \ "name").as[String] == name
    } yield ((groupEntry \ "uuid").as[String], (groupEntry \ "field").as[JsArray])

    VSGlobalMetadataGroup(name, rawGroupEntries.map(tuple=>groupContent(tuple._1, tuple._2)))
  }

  def loadGroups(groupNames:Seq[String], vsBaseUri:String, vsUser:String, vsPasswd:String):Future[Seq[VSGlobalMetadataGroup]] = {
    extractGlobalMetadata(vsBaseUri, vsUser, vsPasswd).map(jsData=>{
      val groups = (
          for {
            timespan <- (jsData \ "timespan").as[JsArray].value
          } yield (timespan \ "group").as[JsArray].value
        ).flatten.toIndexedSeq

      groupNames.map(name=>groupForName(name, groups))
    })

  }
}

