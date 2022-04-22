package models

import java.io.{File, FileInputStream}
import java.nio.file.{Path, Paths}
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import drivers.StorageDriver
import org.slf4j.LoggerFactory
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
                     ctime: Timestamp, mtime: Timestamp, atime: Timestamp, hasContent:Boolean, hasLink:Boolean, backupOf:Option[Int], maybePremiereVersion:Option[Int]) extends PlutoModel {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  /**
    *  writes this model into the database, inserting if id is None and returning a fresh object with id set. If an id
    * was set, then returns the same object. */
  @deprecated("Use FileEntryDAO.save()")
  def save(implicit dao:FileEntryDAO):Future[Try[FileEntry]] = dao.save(this)

  @deprecated("Use FileEntryDAO.saveSimple")
  def saveSimple(implicit dao:FileEntryDAO):Future[FileEntry] = dao.saveSimple(this)

  /**
    *  returns a StorageEntry object for the id of the storage of this FileEntry */
  @deprecated("Use FileEntryDAO.storage")
  def storage(implicit dao:FileEntryDAO):Future[Option[StorageEntry]] = dao.storage(this)

  /**
    * Get a full path of the file, including the root path of the storage
    * @param dao implicitly provided FileEntryDAO instance
    * @return Future containing a string
    */
  @deprecated("Use FileEntryDAO.getFullPath")
  def getFullPath(implicit dao:FileEntryDAO):Future[String] = dao.getFullPath(this)

  /**
    * Gets a java.io.File pointing to the given file
    * @param db
    * @return
    */
  @deprecated("Use FileEntryDAO.getJavaFile")
  def getJavaFile(implicit dao:FileEntryDAO):Future[File] = dao.getJavaFile(this)

  /**
    * this attempts to delete the file from disk, using the configured storage driver
    *
    * @param db implicitly provided [[slick.jdbc.PostgresProfile#Backend#Database]]
    * @return A future containing either a Right() containing a Boolean indicating whether the delete happened,  or a Left with an error string
    */
  @deprecated("Use FileEntryDAO.deleteFromDisk")
  def deleteFromDisk(implicit dao:FileEntryDAO, injector:Injector):Future[Either[String,Boolean]] = dao.deleteFromDisk(this)

  /**
    * attempt to delete the underlying record from the database
    * @param db implicitly provided database object
    * @return a Future with no value on success. On failure, the future fails; pick this up with .recover() or .onComplete
    */
  @deprecated("Use FileEntryDAO.deleteSelf")
  def deleteSelf(implicit dao:FileEntryDAO):Future[Unit] = dao.deleteRecord(this)

  /**
    * Update the hasContent flag
    * @param db implicitly provided [[slick.jdbc.PostgresProfile#Backend#Database]]
    * @return a Future containing a Try, which contains an updated [[models.FileEntry]] instance
    */
  @deprecated("Use FileEntryDAO.updateFileHasContent")
  def updateFileHasContent(implicit dao:FileEntryDAO) = dao.updateFileHasContent(this)

  /* Asynchronously writes the given buffer to this file*/
  @deprecated("Use FileEntryDAO.writeToFile")
  def writeToFile(buffer: RawBuffer)(implicit dao:FileEntryDAO, injector:Injector):Future[Try[Unit]] = dao
    .writeToFile(this, buffer)
    .map(_=>Success( () ))
    .recover({
      case err:Throwable=>
        Failure(err)
    })

  /**
    * check if this FileEntry points to something real on disk
    * @param db - implicitly provided database object
    * @return a Future, containing a Left with a string if there was an error, or a Right with a Boolean flag indicating if the
    *         pointed object exists on the storage
    */
  @deprecated("Use FileEntryDAO.validateFileExists")
  def validatePathExists(implicit dao:FileEntryDAO) = dao.validatePathExists(this)

  /**
    * check if this FileEntry points to something real on disk.
    * intended to be used in streaming/looping contexts, this expects a StorageDriver for the relevant storage
    * to be provided externally rather than provisioning one internally
    *
    * @param db
    * @param driver
    * @return
    */
  @deprecated("Use FileEntryDAO.validateFileExistsDirect")
  def validatePathExistsDirect(implicit dao:FileEntryDAO, driver:StorageDriver) = dao.validatePathExistsDirect(this)

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
  @deprecated("Use FileEntryDAO.backups")
  def backups(forStorage:Option[Int]=None, drop:Int=0, take:Int=100)(implicit dao:FileEntryDAO) = dao.backups(this, forStorage, drop, take)

  @deprecated("Use FileEntryDAO.backupsCount")
  def backupsCount(forStorage:Option[Int]=None)(implicit dao:FileEntryDAO) = dao.backupsCount(this, forStorage)
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

  def maybePremiereVersion = column[Option[Int]]("i_premiere_version")

  def storageFk = foreignKey("fk_storage",storage,TableQuery[StorageEntryRow])(_.id)
  def backupFk = foreignKey("fk_backup_of", backupOf, TableQuery[FileEntryRow])(_.id)

  def * = (id.?,filepath,storage,user,version,ctime,mtime,atime, hasContent, hasLink, backupOf, maybePremiereVersion) <> (FileEntry.tupled, FileEntry.unapply)
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
      (JsPath \ "backupOf").writeNullable[Int] and
      (JsPath \ "premiereVersion").writeNullable[Int]
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
      (JsPath \ "backupOf").readNullable[Int] and
      (JsPath \ "premiereVersion").readNullable[Int]
    )(FileEntry.apply _)
}
