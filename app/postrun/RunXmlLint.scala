package postrun
import java.io.File

import helpers.PostrunDataCache
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import org.apache.commons.io.FileUtils

import sys.process._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import scala.concurrent.ExecutionContext.Implicits.global

object RunXmlLint {
  /**
    * runXmlLint unzips a premiere project file, streams it through xmllint --format and zips it again.
    * this is needed because Adobe's xml parser seems fragile and does not like the output that the scala.xml.RuleTransformer
    * gives us.
    * it is called from the RunXmlLint postrun and from changing the premiere version
    * @param projectFileName file name to change
    * @return a Try, with no value if successful or an error on failure
    */
  def runXmlLint(projectFileName:String) = {
    val originalFile = new File(projectFileName + ".orig")
    val destFileUncompressed = new File(projectFileName + ".ungz")
    val destFile = new File(projectFileName)

    try {
      FileUtils.moveFile(destFile, originalFile)

      val resultCode = ("zcat" #< originalFile #| "xmllint --format -" #> destFileUncompressed).!
      if (resultCode != 0) {
        FileUtils.moveFile(originalFile, destFile)
        Failure(new RuntimeException(s"xmllint chain returned $resultCode"))
      } else {
        val gzipResultCode = ("gzip" #< destFileUncompressed #> destFile).!
        if(gzipResultCode !=0){
          FileUtils.moveFile(originalFile, destFile)
          Failure(new RuntimeException(s"gzip returned $resultCode"))
        }
        FileUtils.forceDelete(originalFile)
        FileUtils.forceDelete(destFileUncompressed)
        Success( () )
      }
    } catch {
      case e:Throwable=>Failure(e)
    }
  }
}

class RunXmlLint extends PojoPostrun {
  override def postrun(projectFileName: String, projectEntry: ProjectEntry, projectType: ProjectType, dataCache: PostrunDataCache, workingGroupMaybe: Option[PlutoWorkingGroup], commissionMaybe: Option[PlutoCommission]): Future[Try[PostrunDataCache]] = Future {
    RunXmlLint
      .runXmlLint(projectFileName)
      .map(_=>dataCache)
  }
}
