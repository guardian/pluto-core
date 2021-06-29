package drivers

import helpers.StorageHelper

import java.io._
import java.nio.file.Paths
import models.StorageEntry
import play.api.Logger
import scala.util.{Failure, Success, Try}

/**
  * Implements a storage driver for regular file paths
  */
class PathStorage(override val storageRef:StorageEntry) extends StorageDriver{
  val logger: Logger = Logger(this.getClass)

  /**
    * return a [[java.io.File]] instance for the given path
    * @param path absolute path on the storage
    * @return [[java.io.File]] representing the given path
    */
  def fileForPath(path: String) = {
    new File(path)
  }

  def getAbsolutePath(path:String) = {
    storageRef.rootpath match {
      case Some(rootpath)=>
        if(path.startsWith(rootpath)) {
          Paths.get(path)
        } else {
          Paths.get(rootpath,path)
        }
      case None=>Paths.get(path)
    }
  }

  override def pathExists(path: String, version:Int): Boolean = fileForPath(path).exists()

  override def writeDataToPath(path: String, version:Int, dataStream: InputStream): Try[Unit] = {
    val finalPath = getAbsolutePath(path)

    Try { this.fileForPath(finalPath.toString) }.flatMap(f=> {
      logger.info(s"Writing data to ${f.getAbsolutePath}")
      val st = new FileOutputStream(f)

      val bytesCopied = Try { StorageHelper.copyStream(dataStream, st) }
      st.close()
      bytesCopied
    }).map(bytesCopied=>{
      logger.info(s"Finished writing $bytesCopied to ${finalPath.toString}")
    })
  }

  def writeDataToPath(path:String, version:Int, data:Array[Byte]):Try[Unit] = Try {
    val finalPath = storageRef.rootpath match {
      case Some(rootpath)=>Paths.get(rootpath,path)
      case None=>Paths.get(path)
    }

    val f = this.fileForPath(finalPath.toString)
    logger.info(s"Writing data to ${f.getAbsolutePath}")
    val st = new FileOutputStream(f)

    st.write(data)
    st.close()
    logger.info(s"Finished writing to ${f.getAbsolutePath}")
  }

  override def deleteFileAtPath(path: String, version:Int): Boolean = {
    val f = this.fileForPath(path)
    logger.info(s"Deleting file at ${f.getAbsolutePath}")
    f.delete()
  }

  override def getWriteStream(path: String, version:Int): Try[OutputStream] = Try {
    val f = getAbsolutePath(path).toFile
    new BufferedOutputStream(new FileOutputStream(f))
  }

  override def getReadStream(path: String, version:Int): Try[InputStream] = {
    val f = getAbsolutePath(path).toFile
    if(f.exists())
      Success(new BufferedInputStream(new FileInputStream(f)))
    else
      Failure(new RuntimeException(s"Path $path does not exist"))
  }

  override def getMetadata(path: String, version:Int): Map[Symbol, String] = {
    val f = getAbsolutePath(path).toFile
    val result = Map(
      Symbol("size")->f.length().toString,
      Symbol("lastModified")->f.lastModified().toString
    )
    logger.debug(s"$path: $result")
    result

  }

  override def supportsVersions: Boolean = false
}
