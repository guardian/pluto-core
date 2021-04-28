package postrun

import helpers.PostrunDataCache
import models.{ProjectEntry, ProjectType}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import java.io.File
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Source

class CreatePointerFileSpec extends Specification with Mockito {
  sequential

  "CreatePointerFile" should {
    "create a text file with the value of the created asset folder in it" in {
      val mockDataCache = mock[PostrunDataCache]
      mockDataCache.get("created_asset_folder") returns Some("/other/path/to/assets")

      val toTest = new CreatePointerFile
      val result = Await.result(
        toTest.postrun("/tmp/fake-project-file.proj", mock[ProjectEntry], mock[ProjectType], mockDataCache, None, None),
        10.seconds
      )

      val expectedFile = new File("/tmp/fake-project-file.ptr")
      expectedFile.exists() must beTrue
      expectedFile.deleteOnExit()

      result must beSuccessfulTry(mockDataCache)
      val readBackSource = Source.fromFile(expectedFile)
      val readBackData = readBackSource.getLines().fold("")(_ + _)
      readBackSource.close()
      readBackData mustEqual "/other/path/to/assets"
    }

    "fail if there is no asset folder set" in {
      val mockDataCache = mock[PostrunDataCache]
      mockDataCache.get("created_asset_folder") returns None

      val toTest = new CreatePointerFile
      val result = Await.result(
        toTest.postrun("/tmp/another-fake-project-file.proj", mock[ProjectEntry], mock[ProjectType], mockDataCache, None, None),
        10.seconds
      )

      val expectedFile = new File("/tmp/another-fake-project-file.ptr")
      expectedFile.exists() must beFalse
      result must beFailedTry
    }
  }
}
