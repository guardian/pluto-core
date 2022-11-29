package mess

import vidispine.VSOnlineOutputMessage
/**
 * Converts an ObjectMatrixEntry object or Vidispine object to an OnlineOutputMessage message
 * for every associated file in a project
 * */

case class OnlineOutputMessage(mediaTier: String,
                               projectId: String,
                               filePath: Option[String],
                               fileSize: Option[Long],
                               itemId: Option[String],
                               nearlineId: String,
                               mediaCategory: String)
object OnlineOutputMessage {
  def apply(file: VSOnlineOutputMessage): OnlineOutputMessage = {
    new OnlineOutputMessage(
      file.mediaTier,
      file.projectIds.head.toString,
      file.filePath,
      None,
      file.itemId,
      file.nearlineId.get,
      file.mediaCategory)
  }
}
