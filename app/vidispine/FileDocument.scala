package vidispine

import org.slf4j.LoggerFactory

import java.net.URI
import scala.util.Try

case class FileItemRef(id:String)

/**
Represents a FileDocument metadata entry from Vidispine. Note that `uri` _can_ be None, e.g. in the case of state==LOST
 */
case class FileDocument(id:String, path:String, uri:Option[Seq[String]], state:String, size:Long, hash:Option[String], timestamp:String,refreshFlag:Int, storage:String, item:Option[FileItemRef]) extends FileDocumentUtils

trait FileDocumentUtils {
  val uri:Option[Seq[String]]
  val id:String

  private val logger = LoggerFactory.getLogger(getClass)
  /**
   * tries to parse the given URI string, check if it's a file: url and if so returns the path segment
   * @param uri String to check
   * @return a Right with the decoded path if successful, or a Left with an error if not
   */
  protected def extractFilePathFromUri(uri:String) = {
    for {
      uri <- Try { URI.create(uri) }.toEither.left.map(_.getMessage)
      withRightScheme <- if(uri.getScheme!="file") Left("Not a file:// URI") else Right(uri)
    } yield withRightScheme.getPath
  }

  /**
   * the `path` field is relative to the storage root. If you want an absolute path, you have to query the URL.
   * Call this method to do so, it will return the path of the first acceptable file: URI or None if there was nothing
   * available.
   * @return the path segment or None if there is none available.
   */
  def getAbsolutePath = {
    val results = uri.getOrElse(Seq()).map(extractFilePathFromUri)
    val successes = results.collect({case Right(path)=>path})
    if(successes.length==1) {
      Some(successes.head)
    } else if(successes.length>1) {
      logger.warn(s"File $id has multiple acceptable file URIs: ${uri.mkString(",")}.  Using the first.")
      Some(successes.head)
    } else {
      val failures = results.collect({case Left(err)=>err})
      if(failures.nonEmpty) {
        logger.error(s"File $id has no file URIs, tried ${uri.mkString(",")}")
        None
      } else {
        logger.error(s"File $id has no URIs to get a path from")
        None
      }
    }
  }
}
