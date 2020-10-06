package services.migrationcomponents

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpCharset, HttpRequest, MediaRange, MediaType}
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.ByteString
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext


object HttpHelper {
  def requestJson(uri:String, user:String, passwd:String)(implicit http:akka.http.scaladsl.HttpExt, system:ActorSystem, mat:Materializer, ec:ExecutionContext) = {
    val auth = Authorization(BasicHttpCredentials(user, passwd))
    val accept = Accept(MediaRange(MediaType.applicationWithFixedCharset("json",HttpCharset.custom("UTF-8"))))
    val req = HttpRequest(uri = uri, headers=Seq(auth, accept))

    http.singleRequest(req).flatMap(response=> {
      val folderSink = Sink.fold[Array[Byte],ByteString](Array())((acc, elem)=>acc ++ elem.toArray)
      val finalBytesFut = response.entity.dataBytes.toMat(folderSink)(Keep.right).run()

      finalBytesFut.map(bytes=> {
        if (response.status == StatusCodes.OK) {
          Right(Json.parse(bytes))
        } else {
          Left(ByteString(bytes).utf8String)
        }
      })
    })
  }
}
