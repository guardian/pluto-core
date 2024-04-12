package models

import slick.jdbc.PostgresProfile.api._
import org.slf4j.LoggerFactory
import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.lifted.TableQuery

case class MissingAssetFileEntry(id: Option[Int], project: Int, filepath: String) extends PlutoModel {
  private lazy val logger = LoggerFactory.getLogger(getClass)
}

/**
  * Table definition for [[MissingAssetFileEntry]] in Slick
  * @param tag
  */
class MissingAssetFileEntryRow(tag:Tag) extends Table[MissingAssetFileEntry](tag, "MissingAssetFileEntry") {
  def id = column[Int]("id",O.PrimaryKey, O.AutoInc)
  def project = column[Int]("k_project_id")
  def projectFk = foreignKey("fk_project",project,TableQuery[ProjectEntryRow])(_.id)
  def filepath = column[String]("s_filepath")

  def * = (id.?,project,filepath) <> (MissingAssetFileEntry.tupled, MissingAssetFileEntry.unapply)
}


/**
  * JSON serialize/deserialize functions. This trait can be mixed into a View to easily process JSON representations of
  * [[MissingAssetFileEntry]]
  */
trait MissingAssetFileEntrySerializer {
  implicit val missingAssetFileWrites: Writes[MissingAssetFileEntry] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "project").write[Int] and
      (JsPath \ "filepath").write[String]
    )(unlift(MissingAssetFileEntry.unapply))

  implicit val missingAssetFileReads: Reads[MissingAssetFileEntry] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "project").read[Int] and
      (JsPath \ "filepath").read[String]
    )(MissingAssetFileEntry.apply _)
}
