package postrun
import com.sun.security.auth.UnixNumericGroupPrincipal
import helpers.PostrunDataCache
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import play.api.Configuration

import java.nio.file.attribute.{GroupPrincipal, PosixFileAttributeView, PosixFilePermission, PosixFilePermissions, UserPrincipal}
import java.nio.file.{FileSystem, Files, Path, Paths}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._
import org.slf4j.LoggerFactory
import sun.nio.fs.UnixUserPrincipals

class UpdateProjectPermissions(config:Configuration) extends PojoPostrun {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * obtain a PosixFileAttributeView for the given Path.
   * Included as a seperate method for test mocking
   * @param path java.nio.Path representing the file we want attributes for
   * @return the PosixFileAttributeView, or throws an exception
   */
  def attributeViewFor(path:Path) = Files.getFileAttributeView(path, classOf[PosixFileAttributeView])

  /**
   * set the requested group ID and permissions on the given file
   * @param projectFilePath file to set permissions on
   * @param wantedGroup a GroupPrincipal object representing the group to set
   * @param wantedPerms a java.util.Set of PosixFilePermission representing the permissions to apply
   * @return a Success with no value on success or a Failure with an exception otherwise
   */
  def setPerms(projectFilePath:Path, wantedGroup:GroupPrincipal, wantedPerms:java.util.Set[PosixFilePermission]) = Try {
    val attrs = attributeViewFor(projectFilePath)
    attrs.setGroup(wantedGroup)
    attrs.setPermissions(wantedPerms)
  }

  /**
   * look up the required POSIX group specified in the server configuration
   * @param fileSystem FileSystem instance representing the Unix filesystem in order to look up unix-style groups
   * @param config play Configuration
   * @return a Success with the given GroupPrincipal, or a Failure if either the required key is not set or not found
   */
  def getWantedGroup(fileSystem:FileSystem, config:Configuration) = {
    config
      .getOptional[String]("postrun.projectPermissions.groupName")
      .map(groupName=>Try {
        val lookup = fileSystem.getUserPrincipalLookupService
        lookup.lookupPrincipalByGroupName(groupName)
      })
      .sequence
      .flatMap({
        case Some(g)=>Success(g)
        case None=>Failure(
          new RuntimeException("You need to specify a valid group name in the application.conf under postrun.projectPermissions.groupName")
        )
      })
  }

  /**
   * look up the required POSIX permissions speficied in the server configuration
   * @param config play Configuration
   * @return a Success with a set of PosixFilePermission, or a Failure if either the required key is not set or mis-formatted
   */
  def getWantedPerms(config:Configuration) = {
    config.getOptional[String]("postrun.projectPermissions.unixPermissions")
      .map(permString=>Try {
        PosixFilePermissions.fromString(permString)
      })
      .sequence
      .flatMap({
        case Some(p)=>Success(p)
        case None=>Failure(
          new RuntimeException("You need to specify a valid set of unix permissions (e.g. -rw-rw-r--) under postrun.projectPermissions.groupName")
        )
      })
  }

  def checkFileExists(filePath:Path) = Try {
    filePath.toFile.exists()
  }

  override def postrun(projectFileName: String,
                       projectEntry: ProjectEntry,
                       projectType: ProjectType,
                       dataCache: PostrunDataCache,
                       workingGroupMaybe: Option[PlutoWorkingGroup],
                       commissionMaybe: Option[PlutoCommission]): Future[Try[PostrunDataCache]] = Future {
    val projectFilePath = Paths.get(projectFileName)

    checkFileExists(projectFilePath) match {
      case Success(true)=>
        (
          getWantedGroup(projectFilePath.getFileSystem, config),
          getWantedPerms(config)
        ) match {
          case (Success(group), Success(permissions))=>
            setPerms(projectFilePath, group, permissions).map(_=>dataCache)
          case (Failure(groupErr), Failure(permsErr))=>
            logger.error(s"Could not get required group or permissions: ${groupErr.getMessage}; ${permsErr.getMessage}")
            Failure(new RuntimeException("Configuration was incorrect, see logs"))
          case (Failure(groupErr), _)=>
            logger.error(s"Could not get required group: ${groupErr.getMessage}")
            Failure(new RuntimeException("Configuration was incorrect, see logs"))
          case (_, Failure(permsErr))=>
            logger.error(s"Could not get required permissions: ${permsErr.getMessage}")
            Failure(new RuntimeException("Configuration was incorrect, see logs"))
        }
      case Success(false)=>
        Failure(new RuntimeException(s"Project file $projectFilePath did not exist"))
      case Failure(err)=>
        logger.error(s"Could not check file existence: ${err.getMessage}")
        Failure(err)
    }
  }
}
