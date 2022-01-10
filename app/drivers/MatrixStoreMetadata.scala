package drivers

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

class MatrixStoreMetadata (fileSize:Long, fileLastModified:ZonedDateTime, fileVersion:Int, omID:String) extends StorageMetadata {
  override def size: Long = fileSize

  override def lastModified: ZonedDateTime = fileLastModified

  def version: Int = fileVersion

  def objectMatrixId: String = omID

  override def toString: String = s"MatrixStore file $omID is version $fileVersion with size $fileSize last modified at ${fileLastModified.format(DateTimeFormatter.ISO_DATE_TIME)}"
}

object MatrixStoreMetadata {
  def apply(fileSize:Long, fileLastModified:Long, fileVersion:Int, omID:String) = {
    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fileLastModified), ZoneId.systemDefault())

    new MatrixStoreMetadata(fileSize, zdt, fileVersion, omID)
  }
}