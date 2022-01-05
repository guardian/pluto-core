package models

import akka.stream.scaladsl.Source
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.sql.Timestamp
import java.time.LocalDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.jdbc.JdbcBackend

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class ProjectEntry (id: Option[Int], projectTypeId: Int, vidispineProjectId: Option[String],
                         projectTitle: String, created:Timestamp, updated:Timestamp, user: String, workingGroupId: Option[Int],
                         commissionId: Option[Int], deletable: Option[Boolean], deep_archive: Option[Boolean],
                         sensitive: Option[Boolean], status:EntryStatus.Value, productionOffice: ProductionOffice.Value)
extends PlutoModel{
  def projectDefaultStorage(implicit db:slick.jdbc.PostgresProfile#Backend#Database): Future[Option[StorageEntry]] = {
    import cats.implicits._
    for {
      defaults <- db.run(TableQuery[DefaultsRow]
        .filter(_.name === "project_storage_id").result)
        .map(_.headOption)
        .map(_.flatMap(entry=>Try { entry.value.toInt }.toOption))
      entry <- defaults.map(entryId=>
        db.run(
          TableQuery[StorageEntryRow].filter(_.id===entryId).result
        ).map(_.headOption)
      ).sequence.map(_.flatten)
    } yield entry
  }

  /**
    * looks up the files known to be associated with this projectEntry in the database
    * @param allVersions if `true`, then all versions are returned across all storages with the highest version first and
    *                    un-set versions last.  If `false`, then only results on the project default storage (assumed unversioned)
    *                    are returned
    * @param db implicitly provided database object
    * @return a Future, containing a Sequence of matching FileEntry objects
    */
  def associatedFiles(allVersions:Boolean)(implicit db:slick.jdbc.PostgresProfile#Backend#Database): Future[Seq[FileEntry]] = {
    def lookupProjectFiles(maybeLimitStorage:Option[Int]) = db.run {
      TableQuery[FileAssociationRow]
        .filter(_.projectEntry===id.get)
        .join(TableQuery[FileEntryRow])
        .on(_.fileEntry===_.id)
        .filterOpt(maybeLimitStorage)(_._2.storage===_)
        .sortBy(_._2.version.desc.nullsLast)
        .result
    }.map(_.map(_._2))

    for {
      defaultStorage <- projectDefaultStorage
      result <- lookupProjectFiles(if(allVersions) None else defaultStorage.flatMap(_.id))
    } yield result
  }

  /**
    * Gets the working group record associated with this project entry
    * @param db implicitly provided database object
    * @return a Future, containing a Try representing whether the db operation succeeded, containing an Option which has the working group, if there is one.
    */
  def getWorkingGroup(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Option[PlutoWorkingGroup]] = {
    workingGroupId match {
      case None=>Future(None)
      case Some(groupId)=>
        db.run(
          TableQuery[PlutoWorkingGroupRow].filter(_.id===groupId).result.asTry
        ).map({
          case Success(matchingEntries)=>matchingEntries.headOption //should only ever be one or zero matches as id is a unique primary key
          case Failure(error)=>throw error
        })
    }
  }

  /**
    * Gets the commission record associated with this project entry
    * @param db implicitly provided database object
    * @return a Future, containing a Try representing whether the db operation succeeded, containing an Option which has the working group, if there is one.
    */
  def getCommission(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Option[PlutoCommission]] = {
    commissionId match {
      case None=>Future(None)
      case Some(commId)=>
        db.run(
          TableQuery[PlutoCommissionRow].filter(_.id===commId).result.asTry
        ).map({
          case Success(matchingEntries)=>matchingEntries.headOption  //should only ever be one or zero matches as id is a unique primary key
          case Failure(error)=>throw error
        })
    }
  }

  /**
    * updates the commission field only
    * @param db
    */
  def saveCommission(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = db.run {
    TableQuery[ProjectEntryRow].filter(_.id===this.id).map(_.commission).update(this.commissionId)
  }

  def save(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[ProjectEntry]] = id match {
    case None=>
      val insertQuery = TableQuery[ProjectEntryRow] returning TableQuery[ProjectEntryRow].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=this).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult)
        case Failure(error)=>Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[ProjectEntryRow].filter(_.id===realEntityId).update(this).asTry
      ).map({
        case Success(rowsAffected)=>Success(this)
        case Failure(error)=>Failure(error)
      })
  }

  def removeFromDatabase(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[Unit]] = id match {
    case Some(realEntityId)=>
      db.run(DBIO.seq(
        TableQuery[FileAssociationRow].filter(_.projectEntry===realEntityId).delete,
        TableQuery[ProjectEntryRow].filter(_.id===realEntityId).delete,
      ).asTry)
    case None=>
      Future(Failure(new RuntimeException("A record must have been saved before it can be removed from the database")))
  }

  /**
    * returns the contents of this record as a string->string map, for passing to postrun actions
    * @return
    */
  def asStringMap:Map[String,String] = {
    Map(
      "projectId"->id.getOrElse("").toString,
      "vidispineProjectId"->vidispineProjectId.getOrElse(""),
      "projectTitle"->projectTitle,
      "projectCreated"->created.toString,
      "projectOwner"->user
    )
  }
}

class ProjectEntryRow(tag:Tag) extends Table[ProjectEntry](tag, "ProjectEntry") {
  import EntryStatusMapper._
  import ProductionOfficeMapper._

  implicit val DateTimeTotimestamp =
    MappedColumnType.base[DateTime, Timestamp]({d=>new Timestamp(d.getMillis)}, {t=>new DateTime(t.getTime, UTC)})

  def id=column[Int]("id",O.PrimaryKey,O.AutoInc)
  def projectType=column[Int]("k_project_type")
  def vidispineProjectId=column[Option[String]]("s_vidispine_id")
  def projectTitle=column[String]("s_title")
  def created=column[Timestamp]("t_created")
  def updated = column[Timestamp]("t_updated")
  def user=column[String]("s_user")
  def workingGroup=column[Option[Int]]("k_working_group")
  def commission=column[Option[Int]]("k_commission")

  def deletable = column[Option[Boolean]]("b_deletable")
  def deep_archive = column[Option[Boolean]]("b_deeparchive")
  def sensitive = column[Option[Boolean]]("b_sensitive")
  def projectTypeKey=foreignKey("fk_project_type",projectType,TableQuery[ProjectTypeRow])(_.id)

  def status = column[EntryStatus.Value]("s_status")
  def productionOffice = column[ProductionOffice.Value]("s_production_office")

  def * = (id.?, projectType, vidispineProjectId, projectTitle, created, updated, user, workingGroup, commission, deletable, deep_archive, sensitive, status, productionOffice) <> (ProjectEntry.tupled, ProjectEntry.unapply)
}

trait ProjectEntrySerializer extends TimestampSerialization {
  import EntryStatusMapper._
  import ProductionOfficeMapper._

  /*https://www.playframework.com/documentation/2.5.x/ScalaJson*/
  implicit val projectEntryWrites:Writes[ProjectEntry] = (
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "projectTypeId").write[Int] and
      (JsPath \ "vidispineId").writeNullable[String] and
      (JsPath \ "title").write[String] and
      (JsPath \ "created").write[Timestamp] and
      (JsPath \ "updated").write[Timestamp] and
      (JsPath \ "user").write[String] and
      (JsPath \ "workingGroupId").writeNullable[Int] and
      (JsPath \ "commissionId").writeNullable[Int] and
      (JsPath \ "deletable").writeNullable[Boolean] and
      (JsPath \ "deep_archive").writeNullable[Boolean] and
      (JsPath \ "sensitive").writeNullable[Boolean] and
      (JsPath \ "status").write[EntryStatus.Value] and
      (JsPath \ "productionOffice").write[ProductionOffice.Value]
    )(unlift(ProjectEntry.unapply))

  implicit val projectEntryReads:Reads[ProjectEntry] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "projectTypeId").read[Int] and
      (JsPath \ "vidispineId").readNullable[String] and
      (JsPath \ "title").read[String] and
      (JsPath \ "created").read[Timestamp] and
      (JsPath \ "updated").read[Timestamp] and
      (JsPath \ "user").read[String] and
      (JsPath \ "workingGroupId").readNullable[Int] and
      (JsPath \ "commissionId").readNullable[Int] and
      (JsPath \ "deletable").readNullable[Boolean] and
      (JsPath \ "deep_archive").readNullable[Boolean] and
      (JsPath \ "sensitive").readNullable[Boolean] and
      (JsPath \ "status").read[EntryStatus.Value] and
      (JsPath \ "productionOffice").read[ProductionOffice.Value]
    )(ProjectEntry.apply _)
}

object ProjectEntry extends ((Option[Int], Int, Option[String], String, Timestamp, Timestamp, String, Option[Int], Option[Int], Option[Boolean], Option[Boolean], Option[Boolean], EntryStatus.Value, ProductionOffice.Value)=>ProjectEntry) {
  def createFromFile(sourceFile: FileEntry, projectTemplate: ProjectTemplate, title:String, created:Option[LocalDateTime],
                     user:String, workingGroupId: Option[Int], commissionId: Option[Int], existingVidispineId: Option[String],
                     deletable: Boolean, deep_archive: Boolean, sensitive: Boolean, productionOffice: ProductionOffice.Value)
                    (implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[ProjectEntry]] = {
    createFromFile(sourceFile, projectTemplate.projectTypeId, title, created, user, workingGroupId, commissionId, existingVidispineId, deletable, deep_archive, sensitive, productionOffice)
  }

  def entryForId(requestedId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[ProjectEntry]] = {
    db.run(
      TableQuery[ProjectEntryRow].filter(_.id===requestedId).result.asTry
    ).map(_.map(_.head))
  }

  def lookupByVidispineId(vsid: String)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[Seq[ProjectEntry]]] =
    db.run(
      TableQuery[ProjectEntryRow].filter(_.vidispineProjectId===vsid).result.asTry
    )

  protected def insertFileAssociation(projectEntryId:Int, sourceFileId:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = db.run(
    (TableQuery[FileAssociationRow]+=(projectEntryId,sourceFileId)).asTry
  )

  private def dateTimeToTimestamp(from: LocalDateTime) = Timestamp.valueOf(from)

  def createFromFile(sourceFile: FileEntry, projectTypeId: Int, title:String, created:Option[LocalDateTime],
                     user:String, workingGroupId: Option[Int], commissionId: Option[Int], existingVidispineId: Option[String],
                     deletable: Boolean, deep_archive: Boolean, sensitive: Boolean, productionOffice: ProductionOffice.Value)
                    (implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[ProjectEntry]] = {

    /* step one - create a new project entry */
    val timestamp = dateTimeToTimestamp(created.getOrElse(LocalDateTime.now()))
    val entry = ProjectEntry(None, projectTypeId, existingVidispineId, title, timestamp, timestamp,
      user, workingGroupId, commissionId, Some(deletable), Some(deep_archive), Some(sensitive), EntryStatus.New, productionOffice)
    val savedEntry = entry.save

    /* step two - set up file association. Project entry must be saved, so this is done as a future map */
    savedEntry.flatMap({
      case Success(projectEntry)=>
        if(projectEntry.id.isEmpty){
          Future(Failure(new RuntimeException("Project entry was not saved before setting up file assoication")))
        } else if(sourceFile.id.isEmpty){
          Future(Failure(new RuntimeException("Source file was not saved before setting up file assoication")))
        } else {
          insertFileAssociation(projectEntry.id.get, sourceFile.id.get).map({
            case Success(affectedRows: Int) => Success(projectEntry) //we are not interested in the rows, but the project entry object
            case Failure(error) => Failure(error)
          })
        }
      case Failure(error)=>Future(Failure(error))
    })
  }

  /**
   * get a sequence of projects that belong to the given commission
   * @param commissionId ID of the commission to search for
   * @param db implicitly provided database profile
   * @return a Future, containing a sequence of zero or more ProjectEntryRow. On error, the future fails; catch this with .recover or .onComplete
   */
  def forCommission(commissionId:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = db.run(
    TableQuery[ProjectEntryRow].filter(_.commission===commissionId).result
  )

  /*
  Returns an Akka source that yields ProjectEntry objects for every project in the given statuses
   */
  def scanProjectsForStatus(status:EntryStatus.Value)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    import EntryStatusMapper._
    Source.fromPublisher(db.stream(TableQuery[ProjectEntryRow].filter(_.status===status).result))
  }
}
