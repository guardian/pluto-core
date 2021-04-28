package helpers

import org.specs2.mutable.Specification

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import scala.concurrent.Await
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.io.Source

class AsyncFileWriterSpec extends Specification {
  "AsyncFileWriter.writeFileAsync" should {
    "write the given byte buffer to a file, returning the number of bytes written" in {
      val fileAttribSet = Set(
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_READ
      )
      val tempFile = Files.createTempFile("pluto-core-test","", PosixFilePermissions.asFileAttribute(fileAttribSet.asJava))
      tempFile.toFile.deleteOnExit()

      val msg = "Hello world!"
      val buf = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8))

      val result = Await.result(AsyncFileWriter.writeFileAsync(tempFile, buf), 10.second)
      result mustEqual msg.length

      //read back the written file data to ensure that it actually went out
      val readBackSource = Source.fromFile(tempFile.toFile)
      val readBackData = readBackSource.getLines().fold("")(_ + _)
      readBackSource.close()
      readBackData mustEqual msg
    }
  }
}
