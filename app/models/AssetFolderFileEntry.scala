package models

import slick.jdbc.PostgresProfile.api._
import java.sql.Timestamp
import drivers.StorageDriver
import org.slf4j.LoggerFactory
import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.lifted.TableQuery
import scala.concurrent.Future

/**
  * This class represents an asset folder file that exists on some [[models.StorageEntry]]
  * @param id - Database id. Could be None
  * @param filepath - [[String]] Path of the file on storage, relative to storage root
  * @param storageId - [[Int]] Database id. of the storage that this lives on. Has a foreign key relation to [[models.StorageEntry]]
  * @param version - [[Int]] Number that increments with every update
  * @param ctime - [[Timestamp]] Creation time
  * @param mtime - [[Timestamp]] Modification time
  * @param atime - [[Timestamp]] Access time
  */
case class AssetFolderFileEntry(id: Option[Int], filepath: String, storageId: Int, version:Int,
                     ctime: Timestamp, mtime: Timestamp, atime: Timestamp, project:Option[Int], backupOf:Option[Int]) extends PlutoModel {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  def getFullPath(implicit dao:AssetFolderFileEntryDAO):Future[String] = dao.getFullPath(this)
  def storage(implicit dao:AssetFolderFileEntryDAO):Future[Option[StorageEntry]] = dao.storage(this)
  def validatePathExists(implicit dao:AssetFolderFileEntryDAO) = dao.validatePathExists(this)
  def validatePathExistsDirect(implicit dao:AssetFolderFileEntryDAO, driver:StorageDriver) = dao.validatePathExistsDirect(this)
}

/**
  * Table definition for [[AssetFolderFileEntry]] in Slick
  * @param tag
  */
class AssetFolderFileEntryRow(tag:Tag) extends Table[AssetFolderFileEntry](tag, "AssetFolderFileEntry") {
  def id = column[Int]("id",O.PrimaryKey, O.AutoInc)
  def filepath = column[String]("s_filepath")
  def storage = column[Int]("k_storage_id")
  def version = column[Int]("i_version")
  def ctime = column[Timestamp]("t_ctime")
  def mtime = column[Timestamp]("t_mtime")
  def atime = column[Timestamp]("t_atime")
  def project = column[Option[Int]]("i_project")
  def backupOf = column[Option[Int]]("i_backup_of")

  def storageFk = foreignKey("fk_storage",storage,TableQuery[StorageEntryRow])(_.id)

  def * = (id.?,filepath,storage,version,ctime,mtime,atime,project,backupOf) <> (AssetFolderFileEntry.tupled, AssetFolderFileEntry.unapply)
}


/**
  * JSON serialize/deserialize functions. This trait can be mixed into a View to easily process JSON representations of
  * [[AssetFolderFileEntry]]
  */
trait AssetFolderFileEntrySerializer extends TimestampSerialization {
  /*https://www.playframework.com/documentation/2.5.x/ScalaJson*/
  implicit val fileWrites: Writes[AssetFolderFileEntry] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "filepath").write[String] and
      (JsPath \ "storage").write[Int] and
      (JsPath \ "version").write[Int] and
      (JsPath \ "ctime").write[Timestamp] and
      (JsPath \ "mtime").write[Timestamp] and
      (JsPath \ "atime").write[Timestamp] and
      (JsPath \ "project").writeNullable[Int] and
      (JsPath \ "backupOf").writeNullable[Int]
    )(unlift(AssetFolderFileEntry.unapply))

  implicit val fileReads: Reads[AssetFolderFileEntry] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "filepath").read[String] and
      (JsPath \ "storage").read[Int] and
      (JsPath \ "version").read[Int] and
      (JsPath \ "ctime").read[Timestamp] and
      (JsPath \ "mtime").read[Timestamp] and
      (JsPath \ "atime").read[Timestamp] and
      (JsPath \ "project").readNullable[Int] and
      (JsPath \ "backupOf").readNullable[Int]
    )(AssetFolderFileEntry.apply _)
}
