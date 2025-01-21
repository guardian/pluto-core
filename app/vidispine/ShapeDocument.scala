package vidispine

import org.slf4j.LoggerFactory


case class VSShapeFile(
                      id: String,
                      path: String,
                      uri: Option[Seq[String]],
                      state: String,
                      size: Long,
                      hash: Option[String],
                      timestamp: String,
                      refreshFlag: Int,
                      storage: String,
                      ) extends FileDocumentUtils {
  def sizeOption = if(size == -1) None else Some(size)
}

/**
 * Simplified Component stanza of the VSShapeDocument, containing just what we are interested in
 * @param id Component id.
 * @param file List of VSShapeFile instances
 */
case class SimplifiedComponent(id:String, file:Seq[VSShapeFile])

case class ShapeDocument(
                        id: String,
                        created: String,
                        essenceVersion: Option[Int],
                        tag: Seq[String],
                        mimeType: Option[Seq[String]],
                        containerComponent: Option[SimplifiedComponent],
                        audioComponent: Option[Seq[SimplifiedComponent]],
                        videoComponent: Option[Seq[SimplifiedComponent]],
                        binaryComponent: Option[Seq[SimplifiedComponent]],
                        ) {
  private val logger = LoggerFactory.getLogger(getClass)

  def getLikelyFile:Option[VSShapeFile] = {
    val audioFiles = audioComponent.getOrElse(Seq.empty[SimplifiedComponent]).flatMap(_.file)
    val videoFiles = videoComponent.getOrElse(Seq.empty[SimplifiedComponent]).flatMap(_.file)
    val binaryFiles = binaryComponent.getOrElse(Seq.empty[SimplifiedComponent]).flatMap(_.file)

    val allComponentFiles = containerComponent match {
      case Some(container) => container.file ++ audioFiles ++ videoFiles ++ binaryFiles
      case None => audioFiles ++ videoFiles ++ binaryFiles
    }
    val fileIdMap = allComponentFiles.foldLeft(Map[String, VSShapeFile]())((acc, elem)=>acc + (elem.id->elem))
    if(fileIdMap.size>1) {
      logger.warn(s"Shape $id with tag(s) ${tag.mkString(";")} has got ${fileIdMap.size} different files attached to it, expected one. Using the first.")
    }
    fileIdMap.headOption.map(_._2)
  }

  def summaryString:String = {
    val tagStr = tag.headOption.getOrElse("[untagged]")
    s"$tagStr:$id"
  }
}
