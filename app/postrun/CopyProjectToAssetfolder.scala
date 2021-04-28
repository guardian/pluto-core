package postrun
import helpers.PostrunDataCache
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import org.slf4j.LoggerFactory
import org.apache.commons.io.FileUtils._

import java.nio.file.{Path, Paths}
import scala.concurrent.Future
import scala.util.{Failure, Try}
import scala.concurrent.ExecutionContext.Implicits.global

class CopyProjectToAssetfolder extends PojoPostrun {
  private val logger = LoggerFactory.getLogger(getClass)

  protected def doCopyFile(from:Path, to:Path) = Try { copyFile(from.toFile, to.toFile) }

  override def postrun(projectFileName: String,
                       projectEntry: ProjectEntry,
                       projectType: ProjectType,
                       dataCache: PostrunDataCache,
                       workingGroupMaybe: Option[PlutoWorkingGroup],
                       commissionMaybe: Option[PlutoCommission]): Future[Try[PostrunDataCache]] = {
    dataCache.get("created_asset_folder").map(path=>Paths.get(path)) match {
      case None=>
        Future(Failure(new RuntimeException("No created_asset_folder value in data cache. This action must depend on make_asset_folder.")))
      case Some(assetFolderPath)=>
        logger.info(s"Will copy project file $projectFileName to $assetFolderPath")
        val projectFileOriginalPath = Paths.get(projectFileName)
        val projectFileNameOnly = projectFileOriginalPath.getFileName
        val targetFilePath = assetFolderPath.resolve(projectFileNameOnly)
        Future(doCopyFile(projectFileOriginalPath, targetFilePath))
          .map(_.map(_=>dataCache)) //we don't modify the data cache here.
    }
  }
}
