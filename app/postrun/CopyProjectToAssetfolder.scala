package postrun
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
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

  protected def doCopyFile(from:Path, to:Path)(implicit mat:Materializer) = {
    FileIO.fromPath(from)
      .runWith(FileIO.toPath(to))
  }
  def attributeViewFor(path:Path) = Files.getFileAttributeView(path, classOf[PosixFileAttributeView])

  protected def preservePermissionsAndOwnership(from:Path, to:Path) = Try {
    var loopCount = 0
    while (!Files.exists(to) && (loopCount < 300)) {
      logger.debug("Waiting for file to exist.")
      loopCount = loopCount + 1
      Thread.sleep(100)
    }

    if (to.getFileName.toString.endsWith(".cpr")) {
      Thread.sleep(4000)
    }

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
        implicit val tempActorSystem = ActorSystem()
        implicit val mat = Materializer.matFromSystem
        logger.info(s"Will copy project file $projectFileName to $assetFolderPath")
        val projectFileOriginalPath = Paths.get(projectFileName)
        val projectFileNameOnly = projectFileOriginalPath.getFileName
        val targetFilePath = assetFolderPath.resolve(projectFileNameOnly)

        for {
          _ <- doCopyFile(projectFileOriginalPath, targetFilePath)
          _ <- Future.fromTry(preservePermissionsAndOwnership(projectFileOriginalPath, targetFilePath))
          result <- tempActorSystem.terminate().map((_)=>Success(dataCache))
        } yield result
    }
  }
}
