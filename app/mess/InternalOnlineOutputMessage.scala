package mess

import mxscopy.models.ObjectMatrixEntry
import mes.OnlineOutputMessage
import vidispine.VSOnlineOutputMessage
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

  def toOnlineOutputMessage(file: ObjectMatrixEntry): OnlineOutputMessage = {
    (file.stringAttribute("GNM_PROJECT_ID"), file.stringAttribute("GNM_TYPE")) match {
      case (Some(projectId), Some(gnmType))=>
        OnlineOutputMessage(
          "NEARLINE",
          Seq(projectId),
          file.pathOrFilename,
          file.maybeGetSize(),
          file.stringAttribute("GNM_VIDISPINE_ITEM"),
          Some(file.oid),
          gnmType
        )
      case _=>
        throw new RuntimeException(s"Objectmatrix file ${file.oid} is missing either GNM_PROJECT_ID or GNM_TYPE fields")
    }
  }

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
