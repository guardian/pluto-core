package services.actors.creation

import akka.actor.{ActorSystem, Props}
import helpers.StorageHelper
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication
import akka.pattern.ask
import drivers.StorageDriver
import models.{FileEntry, ProjectRequestFull, ProjectTemplate, StorageEntry}
import services.actors.creation.GenericCreationActor.{NewProjectRequest, StepSucceded}
import slick.jdbc.PostgresProfile

import scala.concurrent.duration._
import java.time.LocalDateTime
import scala.concurrent.{Await, ExecutionContext, Future}

class CopySourceFileSpec extends Specification with Mockito with utils.BuildMyApp {
  "CopySourceFile!NewProjectRequest" should {
    "copy the required source file via StorageHelper, mark it as with content and reply success" in new WithApplication(buildApp) {
      private implicit val injector = app.injector
      implicit val timeout:akka.util.Timeout = 5.seconds
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private val system:ActorSystem = injector.instanceOf(classOf[ActorSystem])
      implicit val ec:ExecutionContext = injector.instanceOf(classOf[ExecutionContext])

      val mockUpdatedFileEntry = mock[FileEntry]
      mockUpdatedFileEntry.saveSimple(any) returns Future(mock[FileEntry])
      val mockedStorageHelper = mock[StorageHelper]
      mockedStorageHelper.copyFile(any,any)(any) returns Future(mockUpdatedFileEntry)
      val testRef = system.actorOf(Props(new CopySourceFile(dbConfigProvider, mockedStorageHelper)(injector)))

      val mockStorageDriver = mock[StorageDriver]
      val mockStorage = mock[StorageEntry]
      mockStorage.getStorageDriver(any) returns Some(mockStorageDriver)
      val mockSourceFileEntry = mock[FileEntry]
      val mockTemplate = mock[ProjectTemplate]
      mockTemplate.file(any) returns Future(mockSourceFileEntry)
      val rq = mock[ProjectRequestFull]
      rq.destinationStorage returns mockStorage
      rq.projectTemplate returns mockTemplate
      val destFileEntry = mock[FileEntry]
      val datastore = GenericCreationActor.ProjectCreateTransientData(Some(destFileEntry),None,None)
      val requestMsg = NewProjectRequest(rq, Some(LocalDateTime.of(2020,1,2,3,4,5)), datastore)
      val result = Await.result((testRef ? requestMsg).mapTo[CreationMessage], 6.seconds)

      there was one(mockedStorageHelper).copyFile(mockSourceFileEntry, destFileEntry)(dbConfigProvider.get[PostgresProfile].db)
      there was one(mockUpdatedFileEntry).saveSimple(any)
      result must beAnInstanceOf[StepSucceded]
    }
  }
}
