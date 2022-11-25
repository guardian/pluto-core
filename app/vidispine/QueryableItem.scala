package vidispine

import scala.util.Try

/**
 * This interface represents something that you can query like item metadata from Vidispine.
 * This can be an actial item or an update message
 */
trait QueryableItem {
  def filePath: Option[String]
  def itemId: Option[String]
  def fileSize: Option[Long]
  def status: Option[String]
  def essenceVersion: Option[Int]
  def sourceFileId:Option[String]
  def sourceFileIds:Array[String]
  def shapeId: Option[String]
  def shapeTag: Option[String]

  def importSource:Option[String]
}
