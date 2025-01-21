package models

import akka.stream.scaladsl.Source
import drivers.StorageDriver
import org.slf4j.LoggerFactory
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import play.api.mvc.RawBuffer
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.io.{File, FileInputStream, InputStream}
import java.nio.file.{Files, Path, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class FileEntryDAO @Inject() (dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext, injector:Injector) {
  private final val db = dbConfigProvider.get[PostgresProfile].db
  private final val logger = LoggerFactory.getLogger(getClass)

  /**
    *  writes this model into the database, inserting if id is None and returning a fresh object with id set. If an id
    * was set, then returns the same object. */
  def save(entry:FileEntry):Future[Try[FileEntry]] = entry.id match {
    case None=>
      val insertQuery = TableQuery[FileEntryRow] returning TableQuery[FileEntryRow].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=entry).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult)
        case Failure(error)=>Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[FileEntryRow].filter(_.id===realEntityId).update(entry).asTry
      ).map({
        case Success(_)=>Success(entry)
        case Failure(error)=>Failure(error)
      })
  }

  def saveSimple(entry:FileEntry):Future[FileEntry] = save(entry).flatMap({
    case Success(e)=>Future(e)
    case Failure(err)=>Future.failed(err)
  })

  /**
    *  returns a StorageEntry object for the id of the storage of this FileEntry */
  def storage(entry:FileEntry):Future[Option[StorageEntry]] = {
    db.run(
      TableQuery[StorageEntryRow].filter(_.id===entry.storageId).result
    ).map(_.headOption)
  }

  /**
    * Get a full path of the file, including the root path of the storage
    * @param entry FileEntry to query
    * @return Future containing a string
    */
  def getFullPath(entry:FileEntry):Future[String] =
    storage(entry).map({
      case Some(storage)=>
        Paths.get(storage.rootpath.getOrElse(""), entry.filepath).toString
      case None=>
        entry.filepath
    })

  /**
    * Gets a java.io.File pointing to the given file
    * @param entry FileEntry to query
    * @return
    */
  def getJavaPath(entry:FileEntry):Future[Path] = storage(entry)
    .map({
      case Some(storage) =>
        if(storage.storageType=="Local") {
          val p = Paths.get(storage.rootpath.getOrElse(""), entry.filepath)
          if (Files.exists(p)) {
            p
          } else {
            throw new RuntimeException(s"${p.toString} does not exist")
          }
        } else {
          logger.error("Cannot getJavaFile for a project that is on a non-local storage")
          throw new RuntimeException(s"${entry.filepath} with id ${entry.id} is on storage ${storage.id} which is of non-local type ${storage.storageType}")
        }
      case None =>
        val f = new File(entry.filepath)
        if(f.exists()) {
          f.toPath
        } else {
          throw new RuntimeException(s"${f.toString} does not exist")
        }
    })

  def getJavaFile(entry:FileEntry):Future[File] = getJavaPath(entry).map(_.toFile)

  /**
    * this attempts to delete the file from disk, using the configured storage driver
    * @param entry FileEntry to delete
    * @return A future containing either a Right() containing a Boolean indicating whether the delete happened,  or a Left with an error string
    */
  def deleteFromDisk(entry:FileEntry):Future[Either[String,Boolean]] = {
    val maybeStorageDriverFuture = storage(entry).map({
      case Some(storageRef)=>
        storageRef.getStorageDriver
      case None=>
        None
    })

    maybeStorageDriverFuture.flatMap({
      case Some(storagedriver)=>
        getFullPath(entry).map(fullpath=>Right(storagedriver.deleteFileAtPath(fullpath, entry.version)))
      case None=>
        Future(Left("No storage driver configured for storage"))
    })
  }

  /**
    * attempt to delete the underlying record from the database
    * @param entry FileEntry to delete
    * @return a Future with no value on success. On failure, the future fails; pick this up with .recover() or .onComplete
    */
  def deleteRecord(entry:FileEntry):Future[Unit] =
    entry.id match {
      case Some(databaseId)=>
        logger.info(s"Deleting database record for file $databaseId (${entry.filepath} on storage ${entry.storageId})")
        db.run(
          DBIO.seq(
            TableQuery[FileAssociationRow].filter(_.fileEntry===databaseId).delete,
            TableQuery[FileEntryRow].filter(_.id===databaseId).delete
          )
        )
      case None=>
        Future.failed(new RuntimeException("Cannot delete a record that has not been saved to the database"))
    }

  /**
    * private method to (synchronously) write a buffer of content to the underlying file. Called by the public method writeToFile().
    * @param buffer [[play.api.mvc.RawBuffer]] containing content to write
    * @param outputPath String, absolute path to write content to.
    * @param storageDriver [[StorageDriver]] instance to do the actual writing
    * @return a Try containing the unit value
    */
  private def writeContent(entry:FileEntry, buffer: RawBuffer, outputPath:java.nio.file.Path, storageDriver:StorageDriver):Try[Unit] =
    buffer.asBytes() match {
      case Some(bytes) => //the buffer is held in memory
        val logger = Logger(getClass)
        logger.debug("uploadContent: writing memory buffer")
        storageDriver.writeDataToPath(outputPath.toString, entry.version, bytes.toArray)
      case None => //the buffer is on-disk
        val logger = Logger(getClass)
        logger.debug("uploadContent: writing disk buffer")
        val fileInputStream = new FileInputStream(buffer.asFile)
        val result=storageDriver.writeDataToPath(outputPath.toString, entry.version, fileInputStream)
        fileInputStream.close()
        result
    }

  /**
    * Update the hasContent flag
    * @param entry FileEntry to update
    * @return a Future containing a Try, which contains an updated [[models.FileEntry]] instance
    */
  def updateFileHasContent(entry:FileEntry) = entry.id match {
      case Some(recordId)=>
        val updateFileref = entry.copy(hasContent = true)
        db.run (
          TableQuery[FileEntryRow].filter (_.id === recordId).update (updateFileref).asTry
        )
      case None=>
        Future(Failure(new RuntimeException("Can't update a file record that has not been saved")))
    }


  def writeStreamToFile(entry: FileEntry, inputStream: InputStream): Future[Unit] = {
    logger.debug(s"writeStreamToFile called for entry: ${entry.id}")

    storage(entry).flatMap {
      case Some(storage) =>
        storage.getStorageDriver match {
          case Some(storageDriver) =>
            val outputPath = Paths.get(entry.filepath)
            logger.info(s"Preparing to write to $outputPath with storage driver $storageDriver")

            Future {
              try {
                logger.debug("Initiating file write process using storageDriver...")

                // Using storageDriver to write data from inputStream to the path
                val result = storageDriver.writeDataToPath(outputPath.toString, entry.version, inputStream)
                inputStream.close() // Close the stream after writing
                logger.debug("File write process completed. Updating file content status...")

                updateFileHasContent(entry)
                logger.info(s"File successfully written to $outputPath and content status updated.")
                result
              } catch {
                case e: Exception =>
                  logger.error(s"Error occurred during file writing to $outputPath", e)
                  try {
                    inputStream.close()
                  } catch {
                    case closeError: Exception =>
                      logger.error("Error occurred while closing input stream", closeError)
                  }
                  throw e
              }
            }

          case None =>
            val errorMsg = s"No storage driver available for storage ${entry.storageId}"
            logger.error(errorMsg)
            Future.failed(new RuntimeException(errorMsg))
        }

      case None =>
        val errorMsg = s"No storage could be found for ID ${entry.storageId}"
        logger.error(errorMsg)
        Future.failed(new RuntimeException(errorMsg))
    }
  }


  /* Asynchronously writes the given buffer to this file*/
  def writeToFile(entry:FileEntry, buffer: RawBuffer):Future[Unit] = {
    storage(entry).map({
      case Some(storage) =>
        storage.getStorageDriver match {
          case Some(storageDriver) =>
            val outputPath = Paths.get(entry.filepath)
            logger.info(s"Writing to $outputPath with $storageDriver")
            for {
              response <- Future.fromTry(writeContent(entry, buffer, outputPath, storageDriver))
              _ <- updateFileHasContent(entry)
            } yield response
          case None =>
            logger.error(s"No storage driver available for storage ${entry.storageId}")
            Failure(new RuntimeException(s"No storage driver available for storage ${entry.storageId}"))
        }
      case None =>
        logger.error(s"No storage could be found for ID ${entry.storageId}")
        Failure(new RuntimeException(s"No storage could be found for ID ${entry.storageId}"))
    })
  }

  /**
    * check if this FileEntry points to something real on disk
    * @param entry FileEntry to query
    * @return a Future, containing a Left with a string if there was an error, or a Right with a Boolean flag indicating if the
    *         pointed object exists on the storage
    */
  def validatePathExists(entry:FileEntry) =
    for {
      filePath <- getFullPath(entry)
      maybeStorage <- storage(entry)
      result <- Future(
        maybeStorage
        .map(_.validatePathExists(filePath, entry.version)) match {
          case Some(result)=>result
          case None=>Left(s"No storage could be found for ID ${entry.storageId} on file ${entry.id}")
        }
      )
    } yield result

  /**
    * check if this FileEntry points to something real on disk.
    * intended to be used in streaming/looping contexts, this expects a StorageDriver for the relevant storage
    * to be provided externally rather than provisioning one internally
    *
    * @param db
    * @param driver
    * @return
    */
  def validatePathExistsDirect(entry:FileEntry)(implicit driver:StorageDriver) = {
    getFullPath(entry).map(path=>driver.pathExists(path, entry.version))
  }

  private def makeQuery(forId:Int, forStorage:Option[Int]) = {
    val baseQuery = TableQuery[FileEntryRow].filter(_.backupOf===forId)

    forStorage match {
      case Some(storageId) => baseQuery.filter(_.storage===storageId)
      case None=> baseQuery
    }
  }

  /**
    * returns some of the backups for this file.  Results are sorted by most recent version first.
    *
    * If the storage does not support versioning you would expect only one result.
    *
    * @param drop start iterating at this entry
    * @param take only return this many results max
    * @return a Future containing a sequence of FileEntry objects. This fails if there is a problem.
    */
  def backups(entry:FileEntry, forStorage:Option[Int]=None, drop:Int=0, take:Int=100) = entry.id match {
    case None=>
      Future.failed(new RuntimeException("A record must be saved before you can query for backups"))
    case Some(fileId)=>
      logger.info(s"Looking for backups of file with id $fileId on storage $forStorage")
      db.run {
        makeQuery(fileId, forStorage)
          .sortBy(_.version.desc.nullsLast)
          .drop(drop)
          .take(take)
          .result
      }
  }

  def backupsCount(entry:FileEntry, forStorage:Option[Int]=None) = entry.id match {
    case None=>
      Future.failed(new RuntimeException("A record must be saved before you can query for backups"))
    case Some(fileId)=>
      db.run {
        makeQuery(fileId, forStorage)
          .length
          .result
      }
  }

  /* ------- Constructors and such ------- */
  /**
    * Get a [[FileEntry]] instance for the given database ID
    * @param entryId database ID to look up
    * @return a Future, containing an Option that may contain a [[FileEntry]] instance
    */
  def entryFor(entryId: Int):Future[Option[FileEntry]] =
    db.run(
      TableQuery[FileEntryRow].filter(_.id===entryId).result.asTry
    ).map({
      case Success(result)=>
        result.headOption
      case Failure(error)=>throw error
    })

  /**
    * Get a FileEntry instance for the given filename and storage
    * @param fileName file name to search for (exact match to file path)
    * @param storageId storage ID to search for
    * @return a Future, containing a Try that contains a sequnce of zero or more FileEntry instances
    */
  def entryFor(fileName: String, storageId: Int, version:Int):Future[Try[Seq[FileEntry]]] =
    db.run(
      TableQuery[FileEntryRow]
        .filter(_.filepath===fileName)
        .filter(_.storage===storageId)
        .filter(_.version===version)
        .result
        .asTry
    )

  /**
    * improved version of entryFor that returns either one or no entries in a more composable way.
    * This should be all that is needed because of table constraints
    * @param fileName the file name to search for (exact match)
    * @param storageId storage ID to search for
    * @param version version number to search for
    * @return a Future containing either a FileEntry or None. The future fails if there is a problem.
    */
  def singleEntryFor(fileName: String, storageId:Int, version:Int):Future[Option[FileEntry]] =
    db.run(
      TableQuery[FileEntryRow].filter(_.filepath===fileName).filter(_.storage===storageId).filter(_.version===version).result
    ).map(_.headOption)

  def allVersionsFor(fileName: String, storageId: Int):Future[Try[Seq[FileEntry]]] =
    db.run(
      TableQuery[FileEntryRow].filter(_.filepath===fileName).filter(_.storage===storageId).sortBy(_.version.desc.nullsLast).result.asTry
    )


  /**
    * returns a list of matching records for the given file name, ordered by most recent first (if versioning is enabled)
    * @param target file path to query. this should be a relative filepath for the given storage.
    * @param forStorageId limit results to this storage only
    * @return a Future containing a sequence of results
    */
  def findByFilename(target:Path, forStorageId:Option[Int], drop:Int=0, take:Int=100) = {
    val baseQuery = TableQuery[FileEntryRow].filter(_.filepath===target.toString)
    val finalQuery = forStorageId match {
      case Some(storageId)=> baseQuery.filter(_.storage===storageId)
      case None=>baseQuery
    }

    db.run {
      finalQuery.sortBy(_.version.desc.nullsLast).drop(drop).take(take).result
    }
  }

  /**
    * returns a streaming source that lists out all files in the database, optionally limiting to a given storage ID
    * @param forStorageId if provided, limit to this storage ID only
    * @param onlyWithContent if true, then limit to only returning files that have the 'haveContent' field set. Defaults to True.
    * @return an Akka Source, that yields FileEntry objects
    */
  def scanAllFiles(forStorageId:Option[Int], onlyWithContent:Boolean=true) = {
    val baseQuery = TableQuery[FileEntryRow]
    val storageQuery = forStorageId match {
      case Some(storageId)=>baseQuery.filter(_.storage===storageId)
      case None=>baseQuery
    }

    val finalQuery = if(onlyWithContent) storageQuery else storageQuery.filter(_.hasContent===true)

    Source.fromPublisher(db.stream(finalQuery.sortBy(_.mtime.asc).result))
  }
}
