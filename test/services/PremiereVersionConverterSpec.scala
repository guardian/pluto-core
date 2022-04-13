package services

import models.{DisplayedVersion, FileEntry, FileEntryDAO, PremiereVersionTranslation, StorageEntry, StorageStatus}
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication
import slick.jdbc.JdbcProfile
import utils.BuildMyApp
import scala.concurrent.ExecutionContext.Implicits.global
import java.sql.Timestamp
import java.time.LocalDateTime
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.xml.transform.RuleTransformer

class PremiereVersionConverterSpec extends Specification with BuildMyApp {
  "PremiereVersionConverter.ProjectVersionTweaker" should {
    "rewrite an xml fragment with a Version in it to the new value" in {
      val sampleXmlString = """<?xml version="1.0" encoding="UTF-8" ?>
                              |<PremiereData Version="3">
                              |        <Project ObjectRef="1"/>
                              |        <Project ObjectID="1" ClassID="62ad66dd-0dcd-42da-a660-6d8fbde94876" Version="27">
                              |                <Node Version="1">
                              |                        <Properties Version="1">
                              |                                <ProjectViewState.List ObjectID="2" ClassID="aab0946f-7a21-4425-8908-fafa2119e30e" Version="3">
                              |                                        <ProjectViewStates Version="1">
                              |                                                <ProjectViewState Version="1" Index="0">
                              |                                                        <First>390de78d-d7c5-4e81-a05d-8dbb1575e4ab</First>
                              |                                                        <Second ObjectRef="1"/>
                              |                                                </ProjectViewState>
                              |                                        </ProjectViewStates>
                              |                                </ProjectViewState.List>
                              |                         </Properties>
                              |                </Node>
                              |        </Project>
                              |</PremiereData>
                              |""".stripMargin
      val converter = new RuleTransformer(new PremiereVersionConverter.ProjectVersionTweaker(PremiereVersionTranslation(99,"test",DisplayedVersion(1,2,3)), 27))
      val parsed = scala.xml.XML.loadString(sampleXmlString)

      val result = converter.transform(parsed).toString()
      println(result)
      result.contains("""<Project ObjectID="1" ClassID="62ad66dd-0dcd-42da-a660-6d8fbde94876" Version="27">""") must beFalse
      result.contains("""<Project Version="99" ClassID="62ad66dd-0dcd-42da-a660-6d8fbde94876" ObjectID="1">""") must beTrue
    }

    "PremiereVersionConverter.backUpFileToStorage" should {
      "use the correct storage for backups" in new WithApplication(buildApp) {
        private val injector = app.injector
        private implicit val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
        private implicit val db = dbConfigProvider.get[JdbcProfile].db
        val mockBackupService = mock[NewProjectBackup]
        val ts = Timestamp.valueOf(LocalDateTime.now())
        mockBackupService.performBackup(any,any,any) returns {Future(FileEntry(None,"notexistingtestfile",1,"test-user",1,ts,ts,ts,false,false, None, None), FileEntry(None,"notexistingtestfile",1,"test-user",1,ts,ts,ts,false,false, None, None))}
        val toTest = new PremiereVersionConverter(backupService = mockBackupService)
        private implicit val fileEntryDAO:FileEntryDAO = injector.instanceOf[FileEntryDAO]
        val testFile = Await.result(fileEntryDAO.entryFor(58), 10.seconds).get
        val result = Await.result(toTest.backupFileToStorage(testFile), 10.seconds)
        there was one(mockBackupService).performBackup(testFile, None, StorageEntry(Some(1),None,Some("/tmp"),None,"Local",Some("me"),None,None,None,None,false,Some(StorageStatus.UNKNOWN),None))
        result must beSome((FileEntry(None,"notexistingtestfile",1,"test-user",1,ts,ts,ts,false,false, None, None), FileEntry(None,"notexistingtestfile",1,"test-user",1,ts,ts,ts,false,false, None, None)))
      }
    }
  }
}
