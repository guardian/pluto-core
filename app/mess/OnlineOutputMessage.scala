package mess

import mxscopy.models.ObjectMatrixEntry
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
  def apply(file: ObjectMatrixEntry): OnlineOutputMessage = {
    (file.stringAttribute("GNM_PROJECT_ID"), file.stringAttribute("GNM_TYPE")) match {
      case (Some(projectId), Some(gnmType))=>
        new OnlineOutputMessage(
          "NEARLINE",
          projectId,
          file.pathOrFilename,
          file.maybeGetSize(),
          file.stringAttribute("GNM_VIDISPINE_ITEM"),
          file.oid,
          gnmType
        )
      case _=>
        throw new RuntimeException(s"Objectmatrix file ${file.oid} is missing either GNM_PROJECT_ID or GNM_TYPE fields")
    }
  }

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
