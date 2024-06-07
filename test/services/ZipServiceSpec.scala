package services

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.duration._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.specs2.runner.sbtRun.env.executionContext

import java.nio.file.{Files, Path}
import java.io._
import java.util.zip.ZipInputStream
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

class ZipServiceSpec extends Specification with Mockito {

  implicit lazy val actorSystem: ActorSystem = ActorSystem("pluto-core-download", defaultExecutionContext = Some(executionContext))
  implicit lazy val mat: Materializer = Materializer(actorSystem)

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val zipService = new ZipService()

  "ZipService" should {

    "zip project and assets correctly" in {
      // Initialize projectFile and assetDir
      val projectFile: Path = Files.createTempFile("project", ".pproj")
      val assetDir: Path = Files.createTempDirectory("assets")

      // Write some content to the project file and asset directory
      Files.write(projectFile, "Hello, project!".getBytes)
      val assetFile = Files.createFile(assetDir.resolve("asset.mp4"))
      Files.write(assetFile, "Hello, asset!".getBytes)

      // Call the method under test
      val resultSource: Source[ByteString, _] = zipService.zipProjectAndAssets(projectFile.toString, assetDir.toString)

      // Collect the result into a byte array
      val resultFuture = resultSource.runFold(ByteString.empty)(_ ++ _)
      val resultBytes = Await.result(resultFuture, 5.seconds).toArray

      // Check that the result is a valid zip file containing the expected entries
      val zipInputStream = new ZipInputStream(new ByteArrayInputStream(resultBytes))

      val projectEntry = zipInputStream.getNextEntry
      projectEntry.getName must beEqualTo(projectFile.getFileName.toString)
      scala.io.Source.fromInputStream(zipInputStream).mkString must beEqualTo("Hello, project!")

      val assetEntry = zipInputStream.getNextEntry
      assetEntry.getName must beEqualTo(assetDir.getFileName.toString + "/asset.mp4")
      scala.io.Source.fromInputStream(zipInputStream).mkString must beEqualTo("Hello, asset!")

      // Cleanup
      Try(Files.deleteIfExists(projectFile))
      Try(Files.deleteIfExists(assetFile))
      Try(Files.deleteIfExists(assetDir))

      success
    }
  }
}
