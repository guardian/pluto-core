package mess

import mes.OnlineOutputMessage
import vidispine.{SearchResultItemSimplified, VSOnlineOutputMessage}
/**
 * Converts an ObjectMatrixEntry object or Vidispine object to an OnlineOutputMessage message
 * for every associated file in a project
 * */

case class InternalOnlineOutputMessage(mediaTier: String,
                                       projectIds: Seq[Int],
                                       filePath: Option[String],
                                       fileSize: Option[Long],
                                       vidispineItemId: Option[String],
                                       objectId: Option[String],
                                       mediaCategory: String)
object InternalOnlineOutputMessage {

  def toOnlineOutputMessage(file: VSOnlineOutputMessage): OnlineOutputMessage = {
    OnlineOutputMessage(
      file.mediaTier,
      file.projectIds.map(_.toString),
      file.filePath,
      file.fileSize,
      file.itemId,
      file.nearlineId,
      file.mediaCategory)
  }
}
