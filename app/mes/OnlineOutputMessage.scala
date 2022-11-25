package mes

case class OnlineOutputMessage(mediaTier: String,
                               projectIds: Seq[String],
                               originalFilePath: Option[String],
                               fileSize: Option[Long],
                               vidispineItemId: Option[String],
                               nearlineId: Option[String],
                               mediaCategory: String)
