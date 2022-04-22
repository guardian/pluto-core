package postrun
import helpers.PostrunDataCache
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * This postrun tries to parse the project file's XML and extract the internal version number.
  * If successful the version number is placed in the postrun data cache
  */
class ExtractPremiereVersion extends PojoPostrun with AdobeXml {
  private final val logger = LoggerFactory.getLogger(getClass)

  /**
    * takes in a parsed xml NodeSeq from a Premiere file and finds the Version attribute from the first <Project> entry
    * @param seq parsed xml content
    * @return either a string containing the version number or None
    */
  def findVersionNumber(seq: NodeSeq) = {
    seq
      .map(_.attribute("Version"))
      .collectFirst { case Some(attr) => attr.text }
  }

  override def postrun(projectFileName: String, projectEntry: ProjectEntry, projectType: ProjectType, dataCache: PostrunDataCache, workingGroupMaybe: Option[PlutoWorkingGroup], commissionMaybe: Option[PlutoCommission]): Future[Try[PostrunDataCache]] = {
    val maybeVersion = for {
      xmlContent <- getXmlFromGzippedFile(projectFileName)
      projectNodes <- Try { xmlContent \ "Project"  }
      maybeVersionNumberString <- Try { findVersionNumber(projectNodes) }
      maybeVersionNumber <- Try { maybeVersionNumberString.map(_.toInt) }
    } yield maybeVersionNumberString

    maybeVersion match {
      case Success(Some(version))=>
        Future(Success(dataCache.withString("premiere_version", version)))
      case Success(None)=>
        logger.error(s"Could not find a premiere version number in $projectFileName despite it being a valid gzipped xml")
        Future(Failure(new RuntimeException("File contained no premiere version number")))
      case Failure(err)=>
        logger.error(s"Could not extract premiere version number from $projectFileName: ${err.getClass.getCanonicalName} ${err.getMessage}")
        Future(Failure(err))
    }
  }
}
