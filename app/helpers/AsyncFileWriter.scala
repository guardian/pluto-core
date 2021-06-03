package helpers

import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousChannelGroup, AsynchronousFileChannel, CompletionHandler}
import java.nio.file.{Path, StandardOpenOption}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters._

object AsyncFileWriter {
  private val ec = Executors.newCachedThreadPool()
  private val logger = LoggerFactory.getLogger(getClass)

  private class WriteCompletedHandler(completionPromise: Promise[Int]) extends CompletionHandler[Integer, AsynchronousFileChannel] {
    def safeClose(attachment: AsynchronousFileChannel) = Try { attachment.close() } match {
      case Success(_)=>
      case Failure(err)=>
        logger.error(s"Could not close file: ${err.getMessage}", err)
    }

    override def completed(result: Integer, attachment: AsynchronousFileChannel): Unit = {
      safeClose(attachment)
      completionPromise.complete(Success(result))
    }

    override def failed(exc: Throwable, attachment: AsynchronousFileChannel): Unit = {
      safeClose(attachment)
      completionPromise.failure(exc)
    }
  }

  def writeFileAsync(filePath: Path, buffer: ByteBuffer) = {
    val completionPromise = Promise[Int]()

    val ch = AsynchronousFileChannel.open(filePath, Set(StandardOpenOption.WRITE, StandardOpenOption.CREATE).asJava, ec)
    val h = new WriteCompletedHandler(completionPromise)
    ch.write[AsynchronousFileChannel](buffer, 0, ch, h)

    completionPromise.future
  }
}
