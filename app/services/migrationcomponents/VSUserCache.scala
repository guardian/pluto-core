package services.migrationcomponents

import org.slf4j.LoggerFactory
import play.api.libs.json.{JsArray, Json}
import scala.io.Source
import scala.util.{Failure, Success, Try}

object VSUserCache {
  private final val logger = LoggerFactory.getLogger(getClass)

  private def slurpData(filepath:String) = {
    val s = Try { Source.fromFile(filepath, "UTF-8") }
    try {
      s.map(_.mkString)
    } catch {
      case err:Throwable=>
        Failure(err)
    } finally {
      s.map(_.close())
    }
  }

  def initialize(filepath:String):Try[VSUserCache] = {
      slurpData(filepath)
        .map(Json.parse)
        .map(jsonDoc=>{
          jsonDoc.as[JsArray].value.map(userEntry=>{
            (userEntry \ "pk").as[Int] -> (userEntry \ "fields" \ "username").as[String]
          })
        })
        .map(_.toMap)
        .map(mappedData=>new VSUserCache(mappedData))
    }
}

class VSUserCache(private val withContent:Map[Int,String]) {
  def lookup(forId:Int) = withContent.get(forId)
}
