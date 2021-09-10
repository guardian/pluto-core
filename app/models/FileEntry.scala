package models

import java.io.FileInputStream
import java.nio.file.{Path, Paths}
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import drivers.StorageDriver
import play.api.Logger
import play.api.inject.Injector
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.{RawBuffer, Result}
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.{Await, Future}

/**
  * This class represents a file that exists on some [[models.StorageEntry]]
  * @param id - database ID. Could be None
  * @param filepath - [[String]] path of the file on storage, relative to storage root
  * @param storageId - [[Int]] database ID of the storage that this lives on. Has a foreign key relation to [[models.StorageEntry]]
  * @param user - [[String]] person who owns this
  * @param version - [[Int]] number that increments with every update
  * @param ctime - [[Timestamp]] creation time
  * @param mtime - [[Timestamp]] modification time
  * @param atime - [[Timestamp]] access time
  * @param hasContent - boolean flag representing whether this entity has any data in it yet
  * @param hasLink - boolean flag representing whether this entitiy is linked to anything (i.e. a project) yet.
  */
case class FileEntry(id: Option[Int], filepath: String, storageId: Int, user:String, version:Int,
                     ctime: Timestamp, mtime: Timestamp, atime: Timestamp, hasContent:Boolean, hasLink:Boolean, backupOf:Option[Int]) extends PlutoModel {

  /**
    *  writes this model into the database, inserting if id is None and returning a fresh object with id set. If an id
    * was set, then returns the same object. */
  def save(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[Try[FileEntry]] = id match {
    case None=>
      val insertQuery = TableQuery[FileEntryRow] returning TableQuery[FileEntryRow].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=this).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult)
        case Failure(error)=>Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[FileEntryRow].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(_)=>Success(this)
        case Failure(error)=>Failure(error)
      })
  }

  def saveSimple(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[FileEntry] = save.flatMap({
    case Success(e)=>Future(e)
    case Failure(err)=>Future.failed(err)
  })

  /**
    *  returns a StorageEntry object for the id of the storage of this FileEntry */
  def storage(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[Option[StorageEntry]] = {
    db.run(
      TableQuery[StorageEntryRow].filter(_.id===storageId).result
    ).map(_.headOption)
  }

  /**
    * Get a full path of the file, including the root path of the storage
    * @param db implicitly provided [[slick.jdbc.PostgresProfile#Backend#Database]]
    * @return Future containing a string
    */
  def getFullPath(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[String] = {
    this.storage.map({
      case Some(storage)=>
        Paths.get(storage.rootpath.getOrElse(""), filepath).toString
      case None=>
        filepath
    })
  }

  /**
    * this attempts to delete the file from disk, using the configured storage driver
    *
    * @param db implicitly provided [[slick.jdbc.PostgresProfile#Backend#Database]]
    * @return A future containing either a Right() containing a Boolean indicating whether the delete happened,  or a Left with an error string
    */
  def deleteFromDisk(implicit db:slick.jdbc.PostgresProfile#Backend#Database, injector:Injector):Future[Either[String,Boolean]] = {
    /**/
    /*it either returns a Right(), with a boolean indicating whether the delete happened or not, or a Left() with an error string*/
    val maybeStorageDriverFuture = this.storage.map({
      case Some(storage)=>
        storage.getStorageDriver
      case None=>
        None
    })

    maybeStorageDriverFuture.flatMap({
      case Some(storagedriver)=>
        this.getFullPath.map(fullpath=>Right(storagedriver.deleteFileAtPath(fullpath, version)))
      case None=>
        Future(Left("No storage driver configured for storage"))
    })
  }

  /**
    * attempt to delete the underlying record from the database
    * @param db
    * @return
    */
  def deleteSelf(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Either[Throwable, Unit]] =
    id match {
      case Some(databaseId)=>
        val logger = Logger(getClass)
        logger.info(s"Deleting database record for file $databaseId ($filepath on storage $storageId)")
        db.run(
          TableQuery[FileEntryRow].filter(_.id===databaseId).delete.asTry
        ).map({
          case Success(_)=>Right( () )
          case Failure(error)=>Left(error)
        })
      case None=>
        Future(Left(new RuntimeException("Cannot delete a record that has not been saved to the database")))
    }

  /**
    * private method to (synchronously) write a buffer of content to the underlying file. Called by the public method writeToFile().
    * @param buffer [[play.api.mvc.RawBuffer]] containing content to write
    * @param outputPath String, absolute path to write content to.
    * @param storageDriver [[StorageDriver]] instance to do the actual writing
    * @return a Try containing the unit value
    */
  private def writeContent(buffer: RawBuffer, outputPath:java.nio.file.Path, storageDriver:StorageDriver):Try[Unit] =
    buffer.asBytes() match {
      case Some(bytes) => //the buffer is held in memory
        val logger = Logger(getClass)
        logger.debug("uploadContent: writing memory buffer")
        storageDriver.writeDataToPath(outputPath.toString, version, bytes.toArray)
      case None => //the buffer is on-disk
        val logger = Logger(getClass)
        logger.debug("uploadContent: writing disk buffer")
        val fileInputStream = new FileInputStream(buffer.asFile)
        val result=storageDriver.writeDataToPath(outputPath.toString, version, fileInputStream)
        fileInputStream.close()
        result
    }

  /**
    * Update the hasContent flag
    * @param db implicitly provided [[slick.jdbc.PostgresProfile#Backend#Database]]
    * @return a Future containing a Try, which contains an updated [[models.FileEntry]] instance
    */
  def updateFileHasContent(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    id match {
      case Some(recordId)=>
        val updateFileref = this.copy (hasContent = true)

        db.run (
          TableQuery[FileEntryRow].filter (_.id === recordId).update (updateFileref).asTry
        )
      case None=>
        Future(Failure(new RuntimeException("Can't update a file record that has not been saved")))
    }
  }

  /* Asynchronously writes the given buffer to this file*/
  def writeToFile(buffer: RawBuffer)(implicit db:slick.jdbc.PostgresProfile#Backend#Database, injector:Injector):Future[Try[Unit]] = {
    val storageResult = this.storage

    storageResult.map({
      case Some(storage) =>
        storage.getStorageDriver match {
          case Some(storageDriver) =>
            try {
              val logger = Logger(getClass)
              val outputPath = Paths.get(this.filepath)
              logger.info(s"Writing to $outputPath with $storageDriver")
              val response = this.writeContent(buffer, outputPath, storageDriver)
              this.updateFileHasContent
              response
            } catch {
              case ex: Exception =>
                val logger = Logger(getClass)
                logger.error("Unable to write file: ", ex)
                Failure(ex)
            }
          case None =>
            val logger = Logger(getClass)
            logger.error(s"No storage driver available for storage ${this.storageId}")
            Failure(new RuntimeException(s"No storage driver available for storage ${this.storageId}"))
        }
      case None =>
        val logger = Logger(getClass)
        logger.error(s"No storage could be found for ID ${this.storageId}")
        Failure(new RuntimeException(s"No storage could be found for ID ${this.storageId}"))
    })
  }

  /**
    * check if this FileEntry points to something real on disk
    * @param db - implicitly provided database object
    * @return a Future, containing a Left with a string if there was an error, or a Right with a Boolean flag indicating if the
    *         pointed object exists on the storage
    */
  def validatePathExists(implicit db:slick.jdbc.PostgresProfile#Backend#Database, injector:Injector) = {
    val preReqs = for {
      filePath <- getFullPath
      maybeStorage <- storage
    } yield (filePath,maybeStorage)

    preReqs.map(pathAndMaybeStorage=>{
      pathAndMaybeStorage._2.map(_.validatePathExists(pathAndMaybeStorage._1, version))
    }).map({
      case Some(result)=>result
      case None=>Left(s"No storage could be found for ID $storageId on file $id")
    })
  }

  /**
    * check if this FileEntry points to something real on disk.
    * intended to be used in streaming/looping contexts, this expects a StorageDriver for the relevant storage
    * to be provided externally rather than provisioning one internally
    *
    * @param db
    * @param driver
    * @return
    */
  def validatePathExistsDirect(implicit db:slick.jdbc.PostgresProfile#Backend#Database, driver:StorageDriver) = {
    getFullPath.map(path=>driver.pathExists(path, version))
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
    * @param db implicitly provided database object
    * @return a Future containing a sequence of FileEntry objects. This fails if there is a problem.
    */
  def backups(forStorage:Option[Int]=None, drop:Int=0, take:Int=100)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = this.id match {
    case None=>
      Future.failed(new RuntimeException("A record must be saved before you can query for backups"))
    case Some(fileId)=>
      db.run {
          makeQuery(fileId, forStorage)
          .sortBy(_.version.desc.nullsLast)
          .drop(drop)
          .take(take)
          .result
      }
  }

  def backupsCount(forStorage:Option[Int]=None)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = this.id match {
    case None=>
      Future.failed(new RuntimeException("A record must be saved before you can query for backups"))
    case Some(fileId)=>
      db.run {
        makeQuery(fileId, forStorage)
          .length
          .result
      }
  }
}

/**
  * Companion object for the [[FileEntry]] case class
  */
object FileEntry extends ((Option[Int], String, Int, String, Int, Timestamp, Timestamp, Timestamp, Boolean, Boolean, Option[Int])=>FileEntry) {
  /**
    * Get a [[FileEntry]] instance for the given database ID
    * @param entryId database ID to look up
    * @param db database object, instance of [[slick.jdbc.PostgresProfile#Backend#Database]]
    * @return a Future, containing an Option that may contain a [[FileEntry]] instance
    */
  def entryFor(entryId: Int, db:slick.jdbc.PostgresProfile#Backend#Database):Future[Option[FileEntry]] =
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
    * @param db implicitly provided database object, instance of slick.jdbc.PostgresProfile#Backend#Database
    * @return a Future, containing a Try that contains a sequnce of zero or more FileEntry instances
    */
  def entryFor(fileName: String, storageId: Int, version:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[Seq[FileEntry]]] =
    db.run(
      TableQuery[FileEntryRow].filter(_.filepath===fileName).filter(_.storage===storageId).filter(_.version===version).result.asTry
    )

  def allVersionsFor(fileName: String, storageId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[Seq[FileEntry]]] =
    db.run(
      TableQuery[FileEntryRow].filter(_.filepath===fileName).filter(_.storage===storageId).sortBy(_.version.desc.nullsLast).result.asTry
    )


  /**
    * returns a list of matching records for the given file name, ordered by most recent first (if versioning is enabled)
    * @param target file path to query. this should be a relative filepath for the given storage.
    * @param forStorageId limit results to this storage only
    * @param db implicitly provided database object
    * @return a Future containing a sequence of results
    */
  def findByFilename(target:Path, forStorageId:Option[Int], drop:Int=0, take:Int=100)(implicit  db:slick.jdbc.PostgresProfile#Backend#Database) = {
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
    * @param db implicitly provided database access object
    * @return an Akka Source, that yields FileEntry objects
    */
  def scanAllFiles(forStorageId:Option[Int], onlyWithContent:Boolean=true)(implicit  db:slick.jdbc.PostgresProfile#Backend#Database) = {
    val baseQuery = TableQuery[FileEntryRow]
    val storageQuery = forStorageId match {
      case Some(storageId)=>baseQuery.filter(_.storage===storageId)
      case None=>baseQuery
    }

    val finalQuery = if(onlyWithContent) storageQuery else storageQuery.filter(_.hasContent===true)

    Source.fromPublisher(db.stream(finalQuery.sortBy(_.mtime.asc).result))
  }
}

/**
  * Table definition for [[FileEntry]] in Slick
  * @param tag
  */
class FileEntryRow(tag:Tag) extends Table[FileEntry](tag, "FileEntry") {
  def id = column[Int]("id",O.PrimaryKey, O.AutoInc)
  def filepath = column[String]("s_filepath")
  def storage = column[Int]("k_storage_id")
  def version = column[Int]("i_version")
  def user = column[String]("s_user")
  def ctime = column[Timestamp]("t_ctime")
  def mtime = column[Timestamp]("t_mtime")
  def atime = column[Timestamp]("t_atime")

  def hasContent = column[Boolean]("b_has_content")
  def hasLink = column[Boolean]("b_has_link")

  def backupOf = column[Option[Int]]("k_backup_of")
  def storageFk = foreignKey("fk_storage",storage,TableQuery[StorageEntryRow])(_.id)
  def backupFk = foreignKey("fk_backup_of", backupOf, TableQuery[FileEntryRow])(_.id)

  def * = (id.?,filepath,storage,user,version,ctime,mtime,atime, hasContent, hasLink, backupOf) <> (FileEntry.tupled, FileEntry.unapply)
}


/**
  * JSON serialize/deserialize functions. This trait can be mixed into a View to easily process JSON representations of
  * [[FileEntry]]
  */
trait FileEntrySerializer extends TimestampSerialization {
  /*https://www.playframework.com/documentation/2.5.x/ScalaJson*/
  implicit val fileWrites: Writes[FileEntry] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "filepath").write[String] and
      (JsPath \ "storage").write[Int] and
      (JsPath \ "user").write[String] and
      (JsPath \ "version").write[Int] and
      (JsPath \ "ctime").write[Timestamp] and
      (JsPath \ "mtime").write[Timestamp] and
      (JsPath \ "atime").write[Timestamp] and
      (JsPath \ "hasContent").write[Boolean] and
      (JsPath \ "hasLink").write[Boolean] and
      (JsPath \ "backupOf").writeNullable[Int]
    )(unlift(FileEntry.unapply))

  implicit val fileReads: Reads[FileEntry] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "filepath").read[String] and
      (JsPath \ "storage").read[Int] and
      (JsPath \ "user").read[String] and
      (JsPath \ "version").read[Int] and
      (JsPath \ "ctime").read[Timestamp] and
      (JsPath \ "mtime").read[Timestamp] and
      (JsPath \ "atime").read[Timestamp] and
      (JsPath \ "hasContent").read[Boolean] and
      (JsPath \ "hasLink").read[Boolean] and
      (JsPath \ "backupOf").readNullable[Int]
    )(FileEntry.apply _)
}
