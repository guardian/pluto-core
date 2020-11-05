package services.migrationcomponents

import java.sql.Timestamp
import java.time.{LocalDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpRequest, HttpResponse, MediaRange, MediaTypes, StatusCode}
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class VSProjectEntity (override protected val rawData:JsValue) extends VSPlutoEntity {
  def title = {
    Seq(
      getSingle("gnm_project_headline"),
      getSingle("title")
    ).collectFirst({ case Some(value)=>value })
  }

  def owner_id_list = {
    getMeta("gnm_project_username").map(numberStr=>Try { numberStr.toInt }).collect({case Success(n)=>n})
  }

  def boolFromAnyString(key:String) =
    getMeta(key).filter(str=>str!="").nonEmpty

  def isDeletable = boolFromAnyString("gnm_storage_rule_deletable")

  def isSensitive = boolFromAnyString("gnm_storage_rule_sensitive")

  def isDeepArchive = boolFromAnyString("gnm_storage_rule_deep_archive")

  def parent_collection_list = getMeta("__parent_collection")
}

object VSProjectEntity {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply(rawData:JsValue) = new VSProjectEntity(rawData)

  def fromList(listEntries:JsValue):IndexedSeq[VSProjectEntity] = listEntries.as[JsArray].value.map(apply).toIndexedSeq

  private def consumeBody(response:HttpResponse)(implicit mat:Materializer):Future[ByteString] = {
    response.entity.dataBytes.toMat(Sink.reduce[ByteString]((acc, elem)=>acc.concat(elem)))(Keep.right).run()
  }

  /**
    * try to fetch a VSProjectEntity based on a direct metadata lookup for the given item
    * @param vsid vidispine ID to look up
    * @param vsBaseUri base URI to use
    * @param vsUser username
    * @param vsPasswd password
    * @param actorSystem implicitly provided actor system
    * @param mat implicitly provided materializer
    * @return a Future contiaing the VSProjectEntity. The future fails if an error occurs, use onComplete or recover() to handle this.
    */
  def directlyQuery(vsid:String, vsBaseUri:String, vsUser:String, vsPasswd:String)(implicit actorSystem:ActorSystem, mat:Materializer) = {
    implicit val ec:ExecutionContext = actorSystem.dispatcher
    val uri = s"$vsBaseUri/API/collection/$vsid?content=metadata"
    val auth = Authorization(BasicHttpCredentials(vsUser, vsPasswd))
    val accept = Accept(MediaRange(MediaTypes.`application/json`))

    val req = HttpRequest(HttpMethods.GET, uri, headers = Seq(auth, accept))
    Http().singleRequest(req).flatMap(response=>{
      consumeBody(response).map(bodyContent=>{
        response.status match {
          case StatusCodes.OK=>
            val parsedContent = Json.parse(bodyContent.toArray)
            VSProjectEntity(parsedContent)
          case _=>
            logger.error(s"Vidispine system returned error ${response.status}: ${bodyContent.utf8String}")
            throw new RuntimeException("Vidispine system error")
        }
      })
    })
  }
}