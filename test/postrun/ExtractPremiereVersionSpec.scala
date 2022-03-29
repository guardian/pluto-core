package postrun

import helpers.PostrunDataCache
import models.{ProjectEntry, ProjectType}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Success, Try}
import scala.xml.Elem

class ExtractPremiereVersionSpec extends Specification with Mockito {
  "ExtractPremiereVersion.findVersionNumber" should {
    "find a version number in stub xml" in {
      val stubXml = <PremiereData Version="2">
        <Project ObjectRef="1"/>
        <Project ObjectID="1" ClassID="62ad66dd-0dcd-42da-a660-6d8fbde94876" Version="32">
          <Node Version="1">
            <Properties Version="1">
              <ProjectViewState.List ObjectID="2" ClassID="aab0946f-7a21-4425-8908-fafa2119e30e" Version="5">
                </ProjectViewState.List>
              </Properties>
            </Node>
          </Project>
        </PremiereData>

      val toTest = new ExtractPremiereVersion
      toTest.findVersionNumber(stubXml \ "Project" ) must beSome("32")
    }
  }

  "ExtractPremiereVersion.postrun" should {
    "load in xml from filename and extract the version" in {
      val stubXml = <PremiereData Version="2">
        <Project ObjectRef="1"/>
        <Project ObjectID="1" ClassID="62ad66dd-0dcd-42da-a660-6d8fbde94876" Version="32">
          <Node Version="1">
            <Properties Version="1">
              <ProjectViewState.List ObjectID="2" ClassID="aab0946f-7a21-4425-8908-fafa2119e30e" Version="5">
              </ProjectViewState.List>
            </Properties>
          </Node>
        </Project>
      </PremiereData>

      val dataCache = PostrunDataCache()

      val mockGetXml = mock[(String)=>Try[Elem]]
      mockGetXml.apply(any) returns Success(stubXml)
      val toTest = new ExtractPremiereVersion {
        override def getXmlFromGzippedFile(fileName:String) = mockGetXml(fileName)
      }

      val result = Await.result(toTest.postrun("/path/to/projectfile.prproj",mock[ProjectEntry], mock[ProjectType], dataCache, None, None), 2.seconds)
      result must beASuccessfulTry
      there was one(mockGetXml).apply("/path/to/projectfile.prproj")
      result.get.get("premiere_version") must beSome("32")
    }
  }
}
