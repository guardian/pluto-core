package streamcomponents

import models.{EntryStatus, FileEntry, ProductionOffice, ProjectEntry, ProjectType, ValidationEntityClass, ValidationJob, ValidationJobStatus, ValidationJobType}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class FindMistlinkedProjectsComponentSpec extends Specification with Mockito {
  def genFileEntry(filePath:String, storageId:Int) = FileEntry(None,filePath, storageId,"test",1,Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), true, true, None, None)
  val fakeProject = ProjectEntry(
    Some(4567),
    1,
    None,
    "Some test project",
    Timestamp.from(Instant.now()),
    Timestamp.from(Instant.now()),
    "testuser",
    None,
    None,
    None,
    None,
    None,
    EntryStatus.InProduction,
    ProductionOffice.US,
    None,
    None
  )
  val fakeProjectType = ProjectType(
    None,
    "Premiere",
    "Premiere.app",
    "1.2.3.4",
    Some(".prproj")
  )
  val fakeFileList = Seq(
    genFileEntry("somefile1.prproj",2),
    genFileEntry("somefile2.prproj",2),
    genFileEntry("invalidfile.other",2),
    genFileEntry("somefile3.prproj", 3)
  )
  val fakeJob = ValidationJob(None,UUID.fromString("6F35D3E5-3CB8-4A52-B967-3E31A13F2BA0"), "test", ValidationJobType.MislinkedPTR,None,None,ValidationJobStatus.Running, None)

  "FindMislinkedProjectsComponent.ValidateProjectExtension" should {
    "return None if the project type has no extension set" in {
      val toTest = new FindMislinkedProjectsComponent(mock[DatabaseConfigProvider], fakeJob)
      toTest.validateProjectExtension(fakeProject, fakeProjectType.copy(fileExtension = None), fakeFileList) must beNone
    }

    "return a problem if there is an invalid file in the given file list" in {
      val toTest = new FindMislinkedProjectsComponent(mock[DatabaseConfigProvider], fakeJob)
      val result = toTest.validateProjectExtension(fakeProject, fakeProjectType, fakeFileList)
      result must beSome
      result.get.jobId mustEqual fakeJob.uuid
      result.get.entityId mustEqual fakeProject.id.get
      result.get.entityClass mustEqual ValidationEntityClass.ProjectEntry
      result.get.notes must beSome("1 files with wrong extension: invalidfile.other on storage id 2")
    }

    "return None if there are no problem files in the given file list" in {
      val toTest = new FindMislinkedProjectsComponent(mock[DatabaseConfigProvider], fakeJob)
      val okFileList = fakeFileList.filter(_.filepath.endsWith(".prproj"))
      toTest.validateProjectExtension(fakeProject, fakeProjectType, okFileList) must beNone

    }
  }
}
