package drivers

import java.io.{EOFException, InputStream, OutputStream}
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import akka.stream.Materializer
import com.om.mxs.client.japi.{Attribute, Constants, MxsObject, SearchTerm, Vault}
import drivers.objectmatrix.{MXSConnectionBuilder, MxsMetadata, ObjectMatrixEntry}
import helpers.StorageHelper
import models.StorageEntry
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * TODO:
  *  - implement no-write-lock in the protocol
  * @param storageRef [[StorageEntry]] instance that this driver instance is assocaited with
  * @param mat implicitly provided ActorMaterializer
  */
class MatrixStoreDriver(override val storageRef: StorageEntry)(implicit val mat:Materializer) extends StorageDriver {
  private val logger = LoggerFactory.getLogger(getClass)
  lazy val userInfo = {
    val splitter = "\\s*,\\s*".r
    if(storageRef.host.isEmpty){
      Failure(new RuntimeException("Driver requires host field to be set"))
    } else if(storageRef.device.isEmpty){
      Failure(new RuntimeException("Driver requires device to be set"))
    } else if(storageRef.user.isEmpty){
      Failure(new RuntimeException("Driver requires user field to be set"))
    } else if(storageRef.password.isEmpty){
      Failure(new RuntimeException("Driver requires password field to be set"))
    } else {
      val deviceParts = storageRef.device.get.split("\\s*,\\s*")
      if(deviceParts.length!=2){
        Failure(new RuntimeException("Malformed device section, should be {cluster-id},{vault-id}"))
      }
      Try {
        MXSConnectionBuilder(splitter.split(storageRef.host.get),
          storageRef.device.get,
          storageRef.user.get,
          storageRef.password.get
        )
      }
    }
  }

  /**
    * wrapper to perform an operation with a vault pointer and ensure that it is disposed when completed
    * @param blk block to perform operation. This is passed a Vault pointer, and can return anything. The wrapper returns the
    *            value of the block wrapped in a Try indicating whether the operation succeeded or failed; the vault is disposed
    *            either way
    * @tparam A type of return value of the block
    * @return
    */
  def withVault[A](blk:Vault=>Try[A]):Try[A] = {
    for {
      builder <- userInfo
      mxs <- builder.build()
      result <- MXSConnectionBuilder.withVault(mxs, storageRef.device.get)(blk)
    } yield result
  }

  def withObject[A](vault:Vault,oid:String)(blk:MxsObject=>Try[A]):Try[A] = {
    for {
      mxsObj <- Try { vault.getObject(oid) }
      result <- blk(mxsObj)
    } yield result
  }

  def writeAttributesWithRetry(mxsFile: MxsObject, attribs:java.util.Collection[com.om.mxs.client.japi.Attribute], attempt:Int=1):Try[Unit] = try {
    val vw = mxsFile.getAttributeView
    vw.writeAllAttributes(attribs)
    Success( () )
  } catch {
    case err:com.om.mxs.client.internal.TaggedIOException=>
      logger.error(s"Could not write attributes for $mxsFile on attempt $attempt: ", err)
      if(attempt>10){
        logger.error("Could not write after 10 attempts, giving up")
        Failure(err)
      } else {
        Thread.sleep(500 * attempt)
        writeAttributesWithRetry(mxsFile, attribs, attempt+1)
      }
    case err:Throwable=>
      Failure(err)
  }

  /**
    * Directly write an InputStream to the given path, until EOF (blocking)
    * @param path [[String]] absolute path to write
    * @param dataStream [[java.io.FileInputStream]] to write from
    */
  def writeDataToPath(path:String, version:Int, dataStream:InputStream):Try[Unit] = withVault { vault=>
    val mxsFile = lookupPath(vault, path, version) match {
      case None=>
        val fileMeta = newFileMeta(path, version, -1)
        vault.createObject(fileMeta.toAttributes.toArray)
      case Some(oid)=>
        vault.getObject(oid)
    }

    val stream = mxsFile.newOutputStream()
    try {
      val copiedSize = StorageHelper.copyStream(dataStream, stream, 10*1024*1024)
      val updatedFileMeta = newFileMeta(path, version, copiedSize)
      writeAttributesWithRetry(mxsFile, updatedFileMeta.toAttributes.asJavaCollection)
    } catch {
      case err:Throwable=>
        logger.error(s"Could not copy file: ", err)
        Failure(err)
    } finally {
      stream.close()
    }
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

  def newFileMeta(path:String, version:Int, length:Long) = {
    val currentTime = ZonedDateTime.now()

    MxsMetadata(
      stringValues = Map(
        "MXFS_FILENAME_UPPER" -> path.toUpperCase,
        "MXFS_FILENAME"->path,
        "MXFS_PATH"->path.toString,
        "MXFS_MIMETYPE"->"application/octet-stream",
        "MXFS_DESCRIPTION"->s"Projectlocker project $path",
        "MXFS_PARENTOID"->"",
        "MXFS_FILEEXT"->getFileExt(path).getOrElse("")
      ),
      boolValues = Map(
        "MXFS_INTRASH"->false,
      ),
      longValues = Map(
        "DPSP_SIZE"->length,
        "MXFS_MODIFICATION_TIME"->currentTime.toInstant.toEpochMilli,
        "MXFS_CREATION_TIME"->currentTime.toInstant.toEpochMilli,
        "MXFS_ACCESS_TIME"->currentTime.toInstant.toEpochMilli,
      ),
      intValues = Map(
        "MXFS_CREATIONDAY"->currentTime.getDayOfMonth,
        "MXFS_COMPATIBLE"->1,
        "MXFS_CREATIONMONTH"->currentTime.getMonthValue,
        "MXFS_CREATIONYEAR"->currentTime.getYear,
        "MXFS_CATEGORY"->4,  //set type to "document",
        "PROJECTLOCKER_VERSION"->version
      )
    )
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
        val fileMeta = newFileMeta(path, version, data.length)
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
        Success(mxsObject.newInputStream())
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
        Failure(new RuntimeException(s"File $path does not exist"))
      case Some(oid)=>
        withObject(vault, oid) { mxsObject=>
          Success(mxsObject.newOutputStream())
        }
    }
  }

  /**
    * Get a Map of metadata relevant to the specified file.  The contents can vary between implementations, but should always
    * have Symbol("size") (Long converted to String) and Symbol("lastModified") (Long converted to String) members
    * @param path [[String]] Absolute path to open
    * @return [[Map]] of [[Symbol]] -> [[String]] containing metadata about the given file.
    */
  def getMetadata(path:String, version:Int):Map[Symbol,String] = withVault { vault=>
    val resultFuture = findByFilename(vault, path, version).map({
      case None=>
        Map(Symbol("size")->"-1")
      case Some(omEntry)=>
        val fileAttrKeysMap = omEntry.attributes.map(_.toSymbolMap)
        Map(
          Symbol("size")->omEntry.fileAttribues.map(_.size).getOrElse(-1).toString,
          Symbol("lastModified")->omEntry.fileAttribues.map(_.mtime).getOrElse(-1).toString,
          Symbol("version")->fileAttrKeysMap.getOrElse(Symbol("PROJECTLOCKER_VERSION"),-1).toString
        ) ++ fileAttrKeysMap.getOrElse(Map())
    })

    //FIXME for future - update protocol to support a Future being returned
    Try { Await.result(resultFuture, 30.seconds) }
  } match {
    case Success(map)=>map
    case Failure(err)=>
      logger.error(s"Could not get metadata for $path at version $version: ", err)
      Map()
  }

  def versionsForFileWithMetadata(vault:Vault, fileName:String) = {
    val metaFutures = versionsForFile(vault, fileName)
      .map(oid=>
        ObjectMatrixEntry(oid).getMetadata(vault,mat,global)
        .map(entry=>Success(entry))
        .recover({case err:Throwable=>Failure(err)})
      )

    Future.sequence(metaFutures).map(results=>{
      val failures = results.collect({case Failure(err)=>err})
      if(failures.nonEmpty){
        logger.error(failures.map(_.toString).mkString("\n"))
        Left("Could not look up versions, see preceding log message for details")
      } else {
        Right(results.collect({case Success(entry)=>entry}))
      }
    })
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
    logger.debug(s"Lookup $fileName on OM vault ${vault.getId}")

    val searchTerm = SearchTerm.createSimpleTerm(new Attribute(Constants.CONTENT, s"MXFS_FILENAME:$fileName"))
    //val searchTerm = SearchTerm.createSimpleTerm("PROJECTLOCKER_VERSION", version)
    val results = vault.searchObjects(searchTerm, 1).asScala.toSeq

    val versionedMap = results.map(oid=>(vault.getObject(oid).getAttributeView.readInt("PROJECTLOCKER_VERSION"),oid)).toMap
    logger.debug(s"Available versions for $fileName: $versionedMap")

    versionedMap.get(version)
  }

  /**
    * locate files for the given filename, as stored in the metadata. This assumes that one or at most two records will
    * be returned and should therefore be more efficient than using the streaming interface. If many records are expected,
    * this will be inefficient and you should use the streaming interface.
    * this will return a Future to avoid blocking any other lookup requests that would hit the cache
    * @param fileName file name to search for
    * @return a Future, containing either a sequence of zero or more results as String oids or an error
    */
  def findByFilename(vault:Vault, fileName:String, version:Int):Future[Option[ObjectMatrixEntry]] =
    lookupPath(vault, fileName, version) match {
      case Some(oid)=>ObjectMatrixEntry(oid).getMetadata(vault, mat, global).map(entry=>Some(entry))
      case None=>Future(None)
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
