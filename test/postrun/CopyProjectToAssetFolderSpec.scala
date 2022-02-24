package postrun

import helpers.PostrunDataCache
import models.{ProjectEntry, ProjectType}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.Await
import scala.util.{Success, Try}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import org.apache.commons.io.FileUtils
import java.io.File

class CopyProjectToAssetFolderSpec extends Specification with Mockito {
  "CopyProjectToAssetFolder" should {
    "request a file copy from the original project path to the asset folder location" in {
      val mockCopy = mock[(Path,Path)=>Try[Unit]]
      mockCopy.apply(any, any) returns Success( () )
      val mockSetPerms = mock[(Path,Path)=>Try[Unit]]
      mockSetPerms.apply(any, any) returns Success( () )
      val mockDataCache = mock[PostrunDataCache]
      mockDataCache.get("created_asset_folder") returns Some("/other/path/to/assets")

      val toTest = new CopyProjectToAssetfolder {
        override protected def doCopyFile(from: Path, to: Path): Try[Unit] = mockCopy(from, to)

        override protected def preservePermissionsAndOwnership(from: Path, to: Path): Try[Unit] = mockSetPerms(from, to)
      }

      val result = Await.result(
        toTest.postrun("/path/to/projectfile.prj",mock[ProjectEntry],mock[ProjectType], mockDataCache, None,None),
        1.second)

      result must beSuccessfulTry(mockDataCache)
      there was one(mockCopy).apply(
        Paths.get("/path/to/projectfile.prj"),
        Paths.get("/other/path/to/assets/projectfile.prj")
      )
      there was one(mockSetPerms).apply(
        Paths.get("/path/to/projectfile.prj"),
        Paths.get("/other/path/to/assets/projectfile.prj")
      )
    }

    "return a Failure if the created_asset_folder is not set" in {
      val mockCopy = mock[(Path,Path)=>Try[Unit]]
      mockCopy.apply(any, any) returns Success( () )
      val mockDataCache = mock[PostrunDataCache]
      mockDataCache.get("created_asset_folder") returns None

      val toTest = new CopyProjectToAssetfolder {
        override protected def doCopyFile(from: Path, to: Path): Try[Unit] = mockCopy(from, to)
      }

      val result = Await.result(
        toTest.postrun("/path/to/projectfile.prj",mock[ProjectEntry],mock[ProjectType], mockDataCache, None,None),
        1.second)

      result must beFailedTry
      there was no(mockCopy).apply(
        any,
        any
      )
    }

    "check permissions are set correctly if the file has a cpr extension" in {
      FileUtils.deleteQuietly(new File("/tmp/test.cpr"))
      val mockCopy = mock[(Path,Path)=>Try[Unit]]
      mockCopy.apply(any, any) returns Success( () )
      val mockSetPerms = mock[(Path,Path)=>Try[Unit]]
      mockSetPerms.apply(any, any) returns Success( () )
      val mockDataCache = mock[PostrunDataCache]
      mockDataCache.get("created_asset_folder") returns Some("/tmp")

      val toTest = new CopyProjectToAssetfolder {
        override protected def preservePermissionsAndOwnership(from: Path, to: Path): Try[Unit] = mockSetPerms(from, to)
      }

      val result = Await.result(
        toTest.postrun("postrun/tests/data/test.cpr",mock[ProjectEntry],mock[ProjectType], mockDataCache, None,None),
        1.second)

      def attributeViewFor(path:Path) = Files.getFileAttributeView(path, classOf[PosixFileAttributeView])

      val destView = attributeViewFor(Paths.get("/tmp/test.cpr").toAbsolutePath)

      val targetPerms:java.util.Set[PosixFilePermission] = Array(
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_WRITE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE
        ).toSet.asJava

      destView.setPermissions(targetPerms)

      val toTestTwo = new CopyProjectToAssetfolder {
        override protected def doCopyFile(from: Path, to: Path): Try[Unit] = mockCopy(from, to)
      }

      val resultTwo = Await.result(
        toTestTwo.postrun("postrun/tests/data/test.cpr",mock[ProjectEntry],mock[ProjectType], mockDataCache, None,None),
        1.second)

      val destViewTwo = attributeViewFor(Paths.get("/tmp/test.cpr").toAbsolutePath)

      val targetPermsTwo:java.util.Set[PosixFilePermission] = Array(
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
      ).toSet.asJava

      destViewTwo.readAttributes().permissions() mustEqual targetPermsTwo

      FileUtils.deleteQuietly(new File("/tmp/test.cpr"))
    }
  }
}
