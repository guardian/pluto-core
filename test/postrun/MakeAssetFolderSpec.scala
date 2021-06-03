package postrun

import helpers.PostrunDataCache
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

import java.nio.file.{Path, Paths}
import scala.concurrent.Await
import scala.util.{Success, Try}
import scala.concurrent.duration._

class MakeAssetFolderSpec extends Specification with Mockito {
  "MakeAssetFolder" should {
    "request the creation of an asset folder in the right place and set its permissions" in {
      val config = Configuration.from(Map[String,String](
        "postrun.assetFolder.basePath"->"/tmp"
      ))

      val mockSetFinalDirPermissions = mock[(Path)=>Try[Path]]
      mockSetFinalDirPermissions.apply(any) returns Success(Paths.get(""))
      val mockDoCreateDir = mock[Path=>Try[Path]]
      mockDoCreateDir.apply(any) returns Success(Paths.get(""))
      val mockSetFinalDirGroup = mock[Path=>Try[Unit]]
      mockSetFinalDirGroup.apply(any) returns Success( () )

      val toTest = new MakeAssetFolder(config) {
        override protected def setFinalDirPermissions(dirPath: Path): Try[Path] =
          mockSetFinalDirPermissions(dirPath).map(_=>dirPath)

        override protected def doCreateDir(dirPath: Path): Try[Path] =
          mockDoCreateDir(dirPath).map(_=>dirPath)

        override def setFinalDirGroup(dirPath: Path): Try[Unit] = mockSetFinalDirGroup(dirPath)
      }

      val mockProjectEntry = mock[ProjectEntry]
      mockProjectEntry.projectTitle returns "Some pröject title"
      mockProjectEntry.user returns "fred_test"
      val mockWorkingGroup = mock[PlutoWorkingGroup]
      mockWorkingGroup.name returns "My working group"
      val mockComm = mock[PlutoCommission]
      mockComm.title returns "My commission"

      val dataCache = PostrunDataCache()

      val result = Await.result(toTest.postrun("/path/to/some_project_file",
        mockProjectEntry,
        mock[ProjectType],
        dataCache,
        Some(mockWorkingGroup),
        Some(mockComm)), 10.seconds)

      result must beSuccessfulTry
      val expectedPath = Paths.get("/tmp/My_working_group/My_commission/fred_test_Some_pr_ject_title")
      there was one(mockDoCreateDir).apply(expectedPath)
      there was one(mockSetFinalDirPermissions).apply(expectedPath)
      there was one(mockSetFinalDirGroup).apply(expectedPath)
      result.get.get("created_asset_folder") must beSome(expectedPath.toString)
    }
  }
}
