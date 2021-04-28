package postrun
import helpers.PostrunDataCache
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import org.slf4j.LoggerFactory
import play.api.Configuration

import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermissions}
import java.nio.file.{FileSystems, Files, Path, Paths}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._

class MakeAssetFolder(config:Configuration) extends PojoPostrun {
  private val logger = LoggerFactory.getLogger(getClass)
  private val sanitizer = "[^\\w\\d\\-]+".r

  /**
   * replaces any non-standard characters with _ to prevent weird issues and potential
   * security holes
   * @param pathPart section to sanitise
   * @return sanitised string
   */
  protected def sanitize(pathPart:String) = sanitizer.replaceAllIn(pathPart,"_")

  /**
   * build a java.nio.Path representing the asset folder to create
   * @param workingGroupMaybe Optional PlutoWorkingGroup instance, operation fails if this is not set
   * @param commissionMaybe Optional PlutoCommission instance, operation fails if this is not set
   * @param projectEntry ProjectEntry instance
   * @return the asset folder path, or a Left with a descriptive error string
   */
  protected def makeAssetFolderPath(workingGroupMaybe:Option[PlutoWorkingGroup], commissionMaybe:Option[PlutoCommission], projectEntry:ProjectEntry) = {
    (workingGroupMaybe, commissionMaybe, config.getOptional[String]("postrun.assetFolder.basePath")) match {
      case (Some(workingGroup), Some(commission), Some(basePath))=>
        val pathParts = Seq(
          basePath,
          sanitize(workingGroup.name),
          sanitize(commission.title),
          sanitize(s"${projectEntry.user}_${projectEntry.projectTitle}")
        )
        Right(Paths.get(pathParts.head, (pathParts.tail):_*))

      case (None,None,None)=>
        Left("Neither working group nor commission was set so asset folder path could not be created. You also need to set postrun.assetFolder.basePath in the configuration.")
      case (None, None, _)=>
        Left("Neither working group nor commission was set so asset folder path could not be created. ")
      case (_,_,None)=>
        Left("You need to set postrun.assetFolder.basePath in application.conf to the base path within which to create asset folders")
      case (None, _, _)=>
        Left("Working group was not set so asset folder path could not be created")
      case (_, _, None)=>
        Left("Commission was not set so asset folder path could not be created")
    }
  }

  /**
   * create the requested directory in the filesystem, with 555 permissions
   * @param dirPath java.nio.Path representing the directory to create
   * @return a Success with the directory path or Failure on error
   */
  protected def doCreateDir(dirPath:Path) = Try {
    val attrs = PosixFilePermissions.asFileAttribute(
      PosixFilePermissions.fromString("rwxr-xr-x")
    )

    Files.createDirectories(dirPath, attrs)
  }

  protected def findRequestedGroup = {
    config
      .getOptional[String]("postrun.assetFolder.owningGroup")
      .map(groupName=>Try {
        val lookup = FileSystems.getDefault.getUserPrincipalLookupService
        lookup.lookupPrincipalByGroupName(groupName)
      })
      .sequence
  }

  /**
   * open the permissions on the end directory created to ensure that the user and group
   * can write to it
   * @param dirPath java.nio.Path representing the created asset folder
   * @return a Success with the directory path or Failure on error
   */
  protected def setFinalDirPermissions(dirPath:Path) = Try {
    val perms = PosixFilePermissions.fromString("rwxrwxr-x")
    Files.setPosixFilePermissions(dirPath, perms)
  }

  /**
   * try to set the group ownership of the target directory, if a target group has been chosen in the configuration.
   * @param dirPath
   * @return
   */
  protected def setFinalDirGroup(dirPath:Path) =
    findRequestedGroup match {
      case Success(Some(group))=>Try {
        logger.info(s"Updating group ownership of ${dirPath.toString} to ${group.getName}")
        val attrs = Files.getFileAttributeView(dirPath, classOf[PosixFileAttributeView])
        attrs.setGroup(group)
      }
      case Success(None)=>
        logger.info(s"Not changing group ownership of ${dirPath.toString} because no group has been specified in the configuration")
        Success( () )
      case Failure(err)=>
        logger.error(s"Could not change group ownership of ${dirPath.toString} to ${config.getOptional[String]("postrun.assetFolder.owningGroup")}: ${err.getMessage}",err)
        Failure(err)
      }

  override def postrun(projectFileName: String,
                       projectEntry: ProjectEntry,
                       projectType: ProjectType,
                       dataCache: PostrunDataCache,
                       workingGroupMaybe: Option[PlutoWorkingGroup],
                       commissionMaybe: Option[PlutoCommission]): Future[Try[PostrunDataCache]] = Future {
    makeAssetFolderPath(workingGroupMaybe, commissionMaybe, projectEntry) match {
      case Left(problem)=>
        logger.error(s"$projectFileName: Could not build asset folder path: $problem")
        Failure(new RuntimeException("Could not build asset folder path"))
      case Right(assetFolderPath)=>
        logger.info(s"$projectFileName:  Will create asset folder at $assetFolderPath")
        val creationResult = for {
          _ <- doCreateDir(assetFolderPath)
          finalPath <- setFinalDirPermissions(assetFolderPath)
          _ <- setFinalDirGroup(assetFolderPath)
        } yield finalPath

        creationResult.map(_=> {
          dataCache ++ Map("created_asset_folder" -> assetFolderPath.toString)
        })
    }
  }
}
