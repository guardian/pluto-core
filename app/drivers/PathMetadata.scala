package drivers

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}

class PathMetadata (fileSize:Long, fileLastModified:ZonedDateTime) extends StorageMetadata {
  override def size: Long = fileSize

  override def lastModified: ZonedDateTime = fileLastModified

  override def toString: String = s"File with size $size last modified at ${lastModified.format(DateTimeFormatter.ISO_DATE_TIME)}"
}

object PathMetadata {
  def apply(fileSize:Long, fileLastModified:Long) = {
    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fileLastModified), ZoneId.systemDefault())

    new PathMetadata(fileSize, zdt)
  }
}
