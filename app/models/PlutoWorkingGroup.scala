package models

import java.util.UUID

import play.api.Logger
import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.api.libs.functional.syntax._
import slick.lifted.{TableQuery, Tag}
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlProfile.ColumnOption.SqlType

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class PlutoWorkingGroup (id:Option[Int], hide:Boolean, name:String, commissioner_name:String, uuid:Option[String]) extends PlutoModel {
  private val logger = Logger(getClass)
  /**
    *  writes this model into the database, inserting if id is None and returning a fresh object with id set. If an id
    * was set, then updates the database record and returns the same object. */
  def save(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[Try[PlutoWorkingGroup]] = id match {
    case None=>
      val insertQuery = TableQuery[PlutoWorkingGroupRow] returning TableQuery[PlutoWorkingGroupRow].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=this).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult.asInstanceOf[PlutoWorkingGroup])  //maybe only intellij needs the cast here?
        case Failure(error)=>Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[PlutoWorkingGroupRow].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>Failure(error)
      })
  }

  /**
    * returns the contents as a string->string map, for passing to postrun actions
    * @return
    */
  def asStringMap:Map[String,String] = Map(
    "workingGroupName"->name,
    "workingGroupCommissioner"->commissioner_name,
    "workingGroupHide"->{ if(hide) "hidden" else "" }
  )
}

class PlutoWorkingGroupRow(tag:Tag) extends Table[PlutoWorkingGroup](tag, "PlutoWorkingGroup") {
  def id = column[Int]("id",O.PrimaryKey, O.AutoInc)
  def hide = column[Boolean]("b_hide")
  def name = column[String]("s_name")
  def commissioner_name = column[String]("s_commissioner")
  def uuid = column[Option[String]]("u_uuid")
  def * = (id.?, hide, name, commissioner_name, uuid) <> (PlutoWorkingGroup.tupled, PlutoWorkingGroup.unapply)
}

object PlutoWorkingGroup extends ((Option[Int],Boolean, String, String, Option[String])=>PlutoWorkingGroup) {
  def entryForId(id:Int)(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[Option[PlutoWorkingGroup]] = db.run(
    TableQuery[PlutoWorkingGroupRow].filter(_.id===id).result
  ).map(resultSeq=>resultSeq.headOption)

  def entryForUuid(id:UUID)(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[Option[PlutoWorkingGroup]] = db.run {
    TableQuery[PlutoWorkingGroupRow].filter(_.uuid===id.toString).result
  }.map(_.headOption)
}

trait PlutoWorkingGroupSerializer extends TimestampSerialization {
  implicit val workingGroupWrites:Writes[PlutoWorkingGroup] = (
    (JsPath \ "id").writeNullable[Int] and
    (JsPath \ "hide").write[Boolean] and
      (JsPath \ "name").write[String] and
      (JsPath \ "commissioner").write[String] and
      (JsPath \ "uuid").writeNullable[String]
    )(unlift(PlutoWorkingGroup.unapply))

  implicit val workingGroupReads:Reads[PlutoWorkingGroup] = (
    (JsPath \ "id").readNullable[Int] and
    (JsPath \ "hide").read[Boolean] and
      (JsPath \ "name").read[String] and
      (JsPath \ "commissioner").read[String] and
      (JsPath \ "uuid").readNullable[String]
    )(PlutoWorkingGroup.apply _)
}

