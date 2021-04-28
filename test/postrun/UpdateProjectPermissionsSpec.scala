package postrun

import helpers.PostrunDataCache
import models.{ProjectEntry, ProjectType}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

import java.nio.file.Path
import java.nio.file.attribute.{GroupPrincipal, PosixFileAttributeView, PosixFilePermission}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

class UpdateProjectPermissionsSpec extends Specification with Mockito {
  "UpdatePlutoPermissions" should {
    "send a request to change the file permissions" in {
      val config = Configuration.from(Map[String,String](
        "postrun.projectPermissions.groupName" -> "nobody",
        "postrun.projectPermissions.unixPermissions" -> "rw-rw-r--"
      ))
      val mockDataCache = mock[PostrunDataCache]
      val mockAttributeView = mock[PosixFileAttributeView]

      val toTest = new UpdateProjectPermissions(config) {
        override def attributeViewFor(path: Path): PosixFileAttributeView = mockAttributeView
      }

      val result = Await.result(
        toTest.postrun("/path/to/projectfile",mock[ProjectEntry], mock[ProjectType], mockDataCache, None, None),
        10.seconds
      )

      result must beSuccessfulTry(mockDataCache)
      there was one(mockAttributeView).setGroup(argThat((g:GroupPrincipal)=>g.getName=="nobody"))
      there was one(mockAttributeView).setPermissions(argThat((s:java.util.Set[PosixFilePermission])=>{
        val scalaSet = s.asScala
        scalaSet.contains(PosixFilePermission.OWNER_READ) &&
          scalaSet.contains(PosixFilePermission.OWNER_WRITE) &&
          scalaSet.contains(PosixFilePermission.GROUP_READ) &&
          scalaSet.contains(PosixFilePermission.GROUP_WRITE) &&
          scalaSet.contains(PosixFilePermission.OTHERS_READ) &&
          !scalaSet.contains(PosixFilePermission.OWNER_EXECUTE) &&
          !scalaSet.contains(PosixFilePermission.GROUP_EXECUTE) &&
          !scalaSet.contains(PosixFilePermission.OTHERS_WRITE) &&
          !scalaSet.contains(PosixFilePermission.OTHERS_EXECUTE)
      }))
    }
  }
}
