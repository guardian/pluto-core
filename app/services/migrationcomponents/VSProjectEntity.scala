package services.migrationcomponents

import play.api.libs.json._

class VSProjectEntity (private val rawData:JsValue) {
  /**
    * returns a (possibly empty) sequence of strings for each available value of the given field
    * @param key fieldname to look for
    * @return
    */
  def getMeta(key:String) = {
    for {
      timespan <- (rawData \ "metadata" \ "timespan").as[JsArray].value
      fieldBlock <- (timespan \ "field").as[JsArray].value
      valueBlock <- (fieldBlock \ "value").as[JsArray].value if (fieldBlock \ "name").as[String] == key
    } yield (valueBlock \ "value").as[String]
  }

  /**
    * returns an option with None if no fields match or a sequence of values if they do
    * @param key fieldname to look for
    * @return
    */
  def getMetaOptional(key:String) = {
    val s = getMeta(key)
    if(s.isEmpty) {
      None
    } else {
      Some(s)
    }
  }
}

object VSProjectEntity {
  def apply(rawData:JsValue) = new VSProjectEntity(rawData)

  def fromList(listEntries:JsValue):scala.collection.IndexedSeq[VSProjectEntity] = listEntries.as[JsArray].value.map(apply)
}