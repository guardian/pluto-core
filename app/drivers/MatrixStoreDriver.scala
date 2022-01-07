package drivers

import java.io.{InputStream, OutputStream}
import java.time.ZonedDateTime
import akka.stream.Materializer
import com.om.mxs.client.japi.{Attribute, Constants, MxsObject, SearchTerm, Vault}
import drivers.objectmatrix.{MXSConnectionBuilder, MxsMetadata, ObjectMatrixEntry}
import helpers.StorageHelper
import models.StorageEntry
import org.slf4j.LoggerFactory
import play.api.inject.Injector

import java.nio.file.Paths
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * @param storageRef [[StorageEntry]] instance that this driver instance is assocaited with
  * @param mat implicitly provided ActorMaterializer
  */
class MatrixStoreDriver(override val storageRef: StorageEntry)(implicit injector:Injector) extends StorageDriver {
  private val logger = LoggerFactory.getLogger(getClass)

  private val connectionManager = injector.instanceOf(classOf[MXSConnectionManager])

  /**
    * wrapper to perform an operation with a vault pointer and ensure that it is disposed when completed
    * @param blk block to perform operation. This is passed a Vault pointer, and can return any type wrapped in a Try. The wrapper returns the
    *            value of the block wrapped in a Try indicating whether the operation succeeded or failed; the vault is disposed
    *            either way
    * @tparam A type of return value of the block
    * @return the value of the block if successful, or a failure indicating why a connection could not be established
    */
  def withVault[A](blk:Vault=>Try[A]):Try[A] = {
    (storageRef.host, storageRef.device, storageRef.user, storageRef.password) match {
      case (Some(h),Some(d),Some(u),Some(p))=>
        val mxs = connectionManager.getConnection(h,u,p)
        mxs.flatMap(mxs=>MXSConnectionBuilder.withVault(mxs, d)(blk))
      case _=>
        logger.error(s"Storage ${storageRef.id} is misconfigured and is missing at least one of host(s), device, username or password")
        Failure(new RuntimeException(s"Storage ${storageRef.id} is misconfigured"))
    }
  }

  def withObject[A](vault:Vault,oid:String)(blk:MxsObject=>Try[A]):Try[A] = {
    for {
      mxsObj <- Try { vault.getObject(oid) }
      result <- blk(mxsObj)
    } yield result
  }

  /**
    * Directly write an InputStream to the given path, until EOF (blocking)
    * @param path [[String]] absolute path to write
    * @param dataStream [[java.io.FileInputStream]] to write from
    */
  def writeDataToPath(path:String, version:Int, dataStream:InputStream):Try[Unit] = withVault { vault=>
    def getStream() = {
      lookupPath(vault, path, version) match {
        case None =>
          logger.debug(s"Object for $path $version does not exist, creating new...")
          val fileMeta = newFileMeta(path, version, None)
          val mxsFile = vault.createObject(fileMeta.toAttributes.toArray)
          mxsFile.newOutputStream()
        case Some(oid) =>
          logger.debug(s"Object for $path $version already exists at $oid")
          try {
            val mxsFile = vault.getObject(oid)
            mxsFile.newOutputStream()
          } catch {
            case err:java.io.IOException=>
              if(err.getMessage.contains("error 321")) {
                logger.warn(s"Object $oid for $path at $version is marked as 'offline', so creating a new one.")
                val fileMeta = newFileMeta(path, version, None)
                val mxsFile = vault.createObject(fileMeta.toAttributes.toArray)
                mxsFile.newOutputStream()
              } else {
                throw err
              }
          }
      }
    }

    val stream = getStream()

    val result = for {
      copiedSize <- Try { StorageHelper.copyStream(dataStream, stream) }
    } yield copiedSize

    stream.close()
    result.map(copiedSize=>logger.info(s"Copied $path: $copiedSize bytes"))
  }

  /**
    * returns the file extension of the provided filename, or None if there is no extension
    * @param fileNameString filename string
    * @return the content of the last extension
    */
  def getFileExt(fileNameString:String):Option[String] = {
    val re = ".*\\.([^\\.]+)$".r

    fileNameString match {
      case re(xtn) =>
        if (xtn.length < 8) {
          Some(xtn)
        } else {
          logger.warn(s"$xtn does not look like a file extension (too long), assuming no actual extension")
          None
        }
      case _ => None
    }
  }

  def newFileMeta(pathString:String, version:Int, length:Option[Long]) = {
    val currentTime = ZonedDateTime.now()

    val path = Paths.get(pathString)

    val initialMeta = MxsMetadata(
      stringValues = Map(
        "MXFS_FILENAME_UPPER" -> path.toString.toUpperCase,
        "MXFS_FILENAME"->path.getFileName.toString,
        "MXFS_PATH"->path.toString,
        "MXFS_MIMETYPE"->"application/octet-stream",
        "MXFS_DESCRIPTION"->s"Projectlocker project $path",
        "MXFS_PARENTOID"->"",
        "MXFS_FILEEXT"->getFileExt(path.getFileName.toString).getOrElse("")
      ),
      boolValues = Map(
        "MXFS_INTRASH"->false,
      ),
      longValues = Map(
        "MXFS_MODIFICATION_TIME"->currentTime.toInstant.toEpochMilli,
        "MXFS_ACCESS_TIME"->currentTime.toInstant.toEpochMilli,
      ),
      intValues = Map(
        "MXFS_COMPATIBLE"->1,
        "MXFS_CATEGORY"->4,  //set type to "document",
        "PROJECTLOCKER_VERSION"->version
      )
    )

    length match {
      case None=>initialMeta
      case Some(actualLength)=>initialMeta.copy(longValues = initialMeta.longValues + ("DPSP_SIZE"->actualLength))
    }
  }

  /**
    * Directly write a byte array to the given path (blocking)
    * @param path [[String]] absolute path to write
    * @param data [[Array]] (of bytes) -  byte array to output
    * @return a Try indicating success or failure. If successful the Try has a unit value.
    */
  def writeDataToPath(path:String, version:Int, data:Array[Byte]):Try[Unit] = withVault { vault=>
    val mxsFile = lookupPath(vault, path, version) match {
      case None=>
        logger.debug(s"No path found for $path at version $version on ${vault.getId}")
        val fileMeta = newFileMeta(path, version, Some(data.length))
        vault.createObject(fileMeta.toAttributes.toArray)
      case Some(oid)=>
        logger.debug(s"Found entry $oid for path $path at version $version on ${vault.getId}")
        vault.getObject(oid)
    }

    val stream = mxsFile.newOutputStream()
    try {
      stream.write(data)
      Success( () )
    } finally {
      stream.close()
    }
  }

  /**
    * Delete the file at the given path (blocking)
    * @param path [[String]] absolute path to delete
    * @return [[Boolean]] indicating whether the file was deleted or not.
    */
  def deleteFileAtPath(path:String, version:Int):Boolean = withVault { vault=>
    lookupPath(vault, path, version) match {
      case None =>
        logger.error(s"No file to delete at $path with version $version on ${storageRef.repr}")
        Success(false)
      case Some(oid) =>
        withObject(vault, oid) { mxsObject =>
          logger.info(s"Deleting MXS file $oid (path $path, version $version)...")
          mxsObject.delete()
          Success(true)
        }
    }
  } match {
    case Success(result) => result
    case Failure(err) =>
      logger.error(s"Could not delete file at $path on $storageRef: ", err)
      false
  }

  /**
    * Get a relevant type of InputStream to read a file's data
    * @param path [[String]] Absolute path to open
    * @return [[java.io.InputStream]] subclass wrapped in a [[Try]]
    */
  def getReadStream(path:String, version:Int):Try[InputStream] = withVault { vault=>
    lookupPath(vault, path, version) match {
      case None=>
        Failure(new RuntimeException(s"File $path does not exist"))
      case Some(oid)=>
        withObject(vault, oid) { mxsObject=>
          Try { mxsObject.newInputStream() }
        }
    }
  }

  /**
    * Get a relevant type of OutputStream to write a file's data.  this may truncate the file.
    * @param path [[String]] Absolute path to open
    * @return [[java.io.OutputStream]] subclass wrapped in a [[Try]]
    */
  def getWriteStream(path:String, version:Int):Try[OutputStream] = withVault { vault=>
    logger.info(s"Writing to file at path $path with version $version on ${storageRef.repr}")
    lookupPath(vault, path, version) match {
      case None=>
        logger.debug(s"No path found for $path at version $version on ${vault.getId}")
        val fileMeta = newFileMeta(path, version, None)
        Try {
          vault.createObject(fileMeta.toAttributes.toArray)
        }.flatMap(obj=>Try { obj.newOutputStream() })
      case Some(oid)=>
        withObject(vault, oid) { mxsObject=>
          Try { mxsObject.newOutputStream() }
        }
    }
  }

  /**
    * Get a Map of metadata relevant to the specified file.  The contents can vary between implementations, but should always
    * have Symbol("size") (Long converted to String) and Symbol("lastModified") (Long converted to String) members
    * @param path [[String]] Absolute path to open
    * @return [[Map]] of [[Symbol]] -> [[String]] containing metadata about the given file.
    */
  def getMetadata(path:String, version:Int):Option[MatrixStoreMetadata] = withVault { vault=>
    lookupPath(vault, path, version).map(oid=>Try {
      val mxsObj = vault.getObject(oid)
      val attrView = mxsObj.getAttributeView
      val fileAttrs = mxsObj.getMXFSFileAttributeView.readAttributes()

      MatrixStoreMetadata(fileAttrs.size(), fileAttrs.lastModifiedTime(), attrView.readInt("PROJECTLOCKER_VERSION"), oid)
    }).getOrElse(Failure(new RuntimeException(s"File $path at version $version does not exist on this storage")))
  } match {
    case Success(map)=>Some(map)
    case Failure(err)=>
      logger.error(s"Could not get metadata for $path at version $version: ", err)
      None
  }

  /**
    * returns ALL OIDs matching a given filename, i.e. if they have different version numbers
    * @param vault vault to query
    * @param fileName filename to look for
    * @return
    */
  def versionsForFile(vault:Vault, fileName:String) = {
    logger.debug(s"Lookup $fileName on ${vault.getId}")
    val searchTerm = SearchTerm.createSimpleTerm("MXFS_FILENAME", fileName)
    vault.searchObjects(searchTerm, 1).asScala.toSeq
  }

  /**
    * look up a given (unique) path on the storage.
    * @param vault
    * @param fileName
    * @param version
    * @return
    */
  def lookupPath(vault:Vault, fileName:String, version:Int)  = {
    logger.debug(s"Lookup $fileName at version $version on OM vault ${vault.getId}")

    val searchTerm = SearchTerm.createSimpleTerm(Constants.CONTENT, s"""MXFS_FILENAME:\"$fileName\" AND PROJECTLOCKER_VERSION:$version""")
    //val searchTerm = SearchTerm.createSimpleTerm("PROJECTLOCKER_VERSION", version)
    val results = vault.searchObjects(searchTerm, 1).asScala.toSeq

    results.headOption match {
      case Some(entry)=>
        logger.debug(s"Got $entry as the OID for $fileName at version $version")
      case None=>
        logger.info(s"Could not find anything for $fileName at version $version")
        val allVersionsQuery = SearchTerm.createSimpleTerm(Constants.CONTENT, s"""MXFS_FILENAME:\"$fileName\"""")
        val allVersions = vault.searchObjects(allVersionsQuery, 10).asScala.toSeq
        logger.info(s"Found ${allVersions.length} hits for filename $fileName")
        allVersions.foreach(oid=>logger.info(s"\t$fileName: $oid"))
    }
    results.headOption
  }

  /**
    * Does the given path exist on this storage?
    * @param path
    * @return
    */
  def pathExists(path:String, version:Int):Boolean =
    withVault { vault=>
      logger.debug(s"Lookup $path on ${vault.getId}")
      if(path==""){
        Success(true) //checking if blank path exists means check if the vault exists. If we get here, then it should do.
      } else {
        Success(lookupPath(vault, path, version) match {
          case Some(oid)=>
            logger.info(s"Found $oid for $path at version $version")
            true
          case None=>
            logger.info(s"Found nothing for $path at version $version")
            false
        })
      }
    } match {
        case Success(result)=>result
        case Failure(err)=>throw err
    }

  def supportsVersions: Boolean = true

}
