package services.migrationcomponents

import java.io.{File, FileInputStream}
import java.sql.Timestamp
import java.time.LocalDateTime

import org.specs2.mutable.Specification
import play.api.libs.json.Json

class VSProjectEntitySpec extends Specification {
  "VSProjectEntity" should {
    "load in valid data" in {
      val f = new File("test/testdata/vs-project-single.json")
      val inputStream = new FileInputStream(f)
      val jsonData = Json.parse(inputStream)
      inputStream.close()

      val toTest = VSProjectEntity(jsonData)

      toTest.title must beSome("New Normal episode 3 - Work")
      toTest.isSensitive must beFalse
      toTest.isDeepArchive must beTrue
      toTest.isDeletable must beFalse
      toTest.updated must beSome(Timestamp.valueOf(LocalDateTime.of(2020,8,11,13,10,7,496000000)))
      toTest.created must beSome(Timestamp.valueOf(LocalDateTime.of(2020,8,11,12,9,54, 546000000)))
    }
  }
}
