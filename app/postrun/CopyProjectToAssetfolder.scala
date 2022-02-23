package postrun
import helpers.PostrunDataCache
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import org.slf4j.LoggerFactory
import org.apache.commons.io.FileUtils._

import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

class CopyProjectToAssetfolder extends PojoPostrun {
  private val logger = LoggerFactory.getLogger(getClass)

  protected def doCopyFile(from:Path, to:Path) = Try { copyFile(from.toFile, to.toFile) }

  def attributeViewFor(path:Path) = Files.getFileAttributeView(path, classOf[PosixFileAttributeView])

  protected def preservePermissionsAndOwnership(from:Path, to:Path) = Try {
    val sourceView = attributeViewFor(from)
    val destView = attributeViewFor(to)

    try {
      destView.setOwner(sourceView.getOwner)
    } catch {
      case err:Throwable=>
        logger.error(s"Could not set owner of ${to.toString} to ${sourceView.getOwner.toString}: $err", err)
    }

    try {
      destView.setGroup(sourceView.readAttributes().group())
    } catch {
      case err:Throwable=>
        logger.error(s"Could not set group of ${to.toString} to ${sourceView.readAttributes().group()}: $err", err)
    }

    val targetPerms:java.util.Set[PosixFilePermission] = if(to.getFileName.toString.endsWith(".cpr")) {
      logger.info(s"${to.toString} is a cubase project, applying open-permissions workaround...")
      Array(
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
      ).toSet.asJava
    } else {
      sourceView.readAttributes().permissions()
    }
    destView.setPermissions(targetPerms)
  }

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
//        Future(doCopyFile(projectFileOriginalPath, targetFilePath))
//          .map(_.map(_=>dataCache)) //we don't modify the data cache here.

        Future.fromTry(for {
          _ <- doCopyFile(projectFileOriginalPath, targetFilePath)
          _ <- preservePermissionsAndOwnership(projectFileOriginalPath, targetFilePath)
          result <- Success(Success(dataCache))
        } yield result)
    }
  }
}
