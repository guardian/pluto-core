package models

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.sql.Timestamp
import java.time.LocalDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.Configuration
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class ProjectEntry (id: Option[Int], projectTypeId: Int, vidispineProjectId: Option[String],
                         projectTitle: String, created:Timestamp, updated:Timestamp, user: String, workingGroupId: Option[Int],
                         commissionId: Option[Int], deletable: Option[Boolean], deep_archive: Option[Boolean],
                         sensitive: Option[Boolean], status:EntryStatus.Value, productionOffice: ProductionOffice.Value, isObitProject:Option[String])
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

  private def projectFilesLookupQuery(maybeLimitStorage:Option[Int]) = TableQuery[FileAssociationRow]
    .filter(_.projectEntry===id.get)
    .join(TableQuery[FileEntryRow])
    .on(_.fileEntry===_.id)
    .filterOpt(maybeLimitStorage)(_._2.storage===_)
    .sortBy(_._2.version.desc.nullsLast)

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
      projectFilesLookupQuery(maybeLimitStorage).result
    }.map(_.map(_._2))

    for {
      defaultStorage <- projectDefaultStorage
      result <- lookupProjectFiles(if(allVersions) None else defaultStorage.flatMap(_.id))
    } yield result
  }

  def mostRecentBackup(implicit db:slick.jdbc.PostgresProfile#Backend#Database, mat:Materializer) = {
    Source.fromPublisher(db.stream(projectFilesLookupQuery(None).result))
      .toMat(Sink.headOption)(Keep.right)
      .run()
      .map(_.map(_._2))
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

  private def projectAssetFolderFilesLookupQuery(maybeLimitStorage:Option[Int]) = TableQuery[AssetFolderFileEntryRow]
    .filter(_.project===id.get)
    .filterOpt(maybeLimitStorage)(_.storage===_)
    .sortBy(_.version.desc.nullsLast)

  def associatedAssetFolderFiles(allVersions:Boolean, configuration: Configuration)(implicit db:slick.jdbc.PostgresProfile#Backend#Database): Future[Seq[AssetFolderFileEntry]] = {
    def lookupProjectAssetFolderFiles(maybeLimitStorage:Option[Int]) = db.run {
      projectAssetFolderFilesLookupQuery(maybeLimitStorage).result
    }

    for {
      result <- lookupProjectAssetFolderFiles(if(allVersions) None else configuration.getOptional[Int]("asset_folder_backup_storage"))
    } yield result
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

  def isObitProject = column[Option[String]]("s_is_obit_project")

  def * = (id.?, projectType, vidispineProjectId, projectTitle, created, updated, user, workingGroup, commission, deletable, deep_archive, sensitive, status, productionOffice, isObitProject) <> (ProjectEntry.tupled, ProjectEntry.unapply)
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
      (JsPath \ "productionOffice").write[ProductionOffice.Value] and
    (JsPath \ "isObitProject").writeNullable[String]
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
      (JsPath \ "productionOffice").read[ProductionOffice.Value] and
      (JsPath \ "isObitProject").readNullable[String]
    )(ProjectEntry.apply _)
}

object ProjectEntry extends ((Option[Int], Int, Option[String], String, Timestamp, Timestamp, String, Option[Int], Option[Int], Option[Boolean], Option[Boolean], Option[Boolean], EntryStatus.Value, ProductionOffice.Value, Option[String])=>ProjectEntry) {

  def getProjectsEligibleForStatusChange(newStatus: EntryStatus.Value, commissionId: Int): DBIO[Seq[(Int, ProjectEntry)]] = {
    import EntryStatusMapper._

    def getProjects(query: Query[ProjectEntryRow, ProjectEntry, Seq]) = {
      query.result.map(projects => projects.map(p => (p.id.getOrElse(-1), p)))
    }

    val baseQuery = TableQuery[ProjectEntryRow].filter(_.commission === commissionId)

    newStatus match {
      case EntryStatus.Completed | EntryStatus.Killed =>
        val filteredQuery = baseQuery
          .filter(_.status =!= EntryStatus.Completed)
          .filter(_.status =!= EntryStatus.Killed)
        getProjects(filteredQuery)

      case EntryStatus.Held =>
        val filteredQuery = baseQuery
          .filter(_.status =!= EntryStatus.Completed)
          .filter(_.status =!= EntryStatus.Killed)
          .filter(_.status =!= EntryStatus.Held)
        getProjects(filteredQuery)

      case _ => DBIO.successful(Seq.empty[(Int, ProjectEntry)])
    }
  }


  def createFromFile(sourceFile: FileEntry, projectTemplate: ProjectTemplate, title:String, created:Option[LocalDateTime],
                     user:String, workingGroupId: Option[Int], commissionId: Option[Int], existingVidispineId: Option[String],
                     deletable: Boolean, deep_archive: Boolean, sensitive: Boolean, productionOffice: ProductionOffice.Value, isObitProject:Option[String])
                    (implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[ProjectEntry]] = {
    createFromFile(sourceFile, projectTemplate.projectTypeId, title, created, user, workingGroupId, commissionId, existingVidispineId, deletable, deep_archive, sensitive, productionOffice, isObitProject)
  }

  def entryForId(requestedId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[ProjectEntry]] = {
    db.run(
      TableQuery[ProjectEntryRow].filter(_.id===requestedId).result.asTry
    ).map(_.map(_.head))
  }

  def entryForIdNew(requestedId: Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[ProjectEntry] =
    db.run(
      TableQuery[ProjectEntryRow].filter(_.id===requestedId).result
    ).map(_.head)

  def lookupByVidispineId(vsid: String)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[Seq[ProjectEntry]]] =
    db.run(
      TableQuery[ProjectEntryRow].filter(_.vidispineProjectId===vsid).result.asTry
    )

  protected def insertFileAssociation(projectEntryId:Int, sourceFileId:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = db.run(
    (TableQuery[FileAssociationRow]+=(projectEntryId,sourceFileId)).asTry
  )

  private def dateTimeToTimestamp(from: LocalDateTime) = Timestamp.valueOf(from)

  def projectForFileEntry(fileEntry:FileEntry)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    db.run {
      TableQuery[FileAssociationRow]
        .filter(_.fileEntry===fileEntry.id)
        .join(TableQuery[ProjectEntryRow])
        .on(_.projectEntry===_.id)
        .map(_._2)
        .result
    }.map(_.headOption)
  }

  def createFromFile(sourceFile: FileEntry, projectTypeId: Int, title:String, created:Option[LocalDateTime],
                     user:String, workingGroupId: Option[Int], commissionId: Option[Int], existingVidispineId: Option[String],
                     deletable: Boolean, deep_archive: Boolean, sensitive: Boolean, productionOffice: ProductionOffice.Value, isObitProject:Option[String])
                    (implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Try[ProjectEntry]] = {

    /* step one - create a new project entry */
    val timestamp = dateTimeToTimestamp(created.getOrElse(LocalDateTime.now()))
    val entry = ProjectEntry(None, projectTypeId, existingVidispineId, title, timestamp, timestamp,
      user, workingGroupId, commissionId, Some(deletable), Some(deep_archive), Some(sensitive), EntryStatus.New, productionOffice, isObitProject)
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
    Source.fromPublisher(db.stream(TableQuery[ProjectEntryRow].filter(_.status===status).sortBy(_.created.desc).result))
  }

  def scanProjectsForStatusAndTypes(status:EntryStatus.Value, projectTypes:Array[Int])(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    import EntryStatusMapper._
    Source.fromPublisher(db.stream(TableQuery[ProjectEntryRow].filter(_.status===status).filter(_.projectType inSet(projectTypes)).sortBy(_.created.desc).result))
  }

  def scanProjectsForTypes(projectTypes:Array[Int])(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    Source.fromPublisher(db.stream(TableQuery[ProjectEntryRow].filter(_.projectType inSet(projectTypes)).sortBy(_.created.desc).result))
  }

  def scanAllProjects(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    Source.fromPublisher(db.stream(TableQuery[ProjectEntryRow].sortBy(_.created.desc).result))
  }

  /**
    * Returns a list of distinct known users starting with the given prefix
    * @param prefix restricts results to only users starting with this string
    * @param limit only return up to this many results
    * @param db implicitly provided database object
    * @return a Future, containing a sequence of matching usernames
    */
  def listUsers(prefix:String, limit:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = db.run {
    val lowerCasePrefix = prefix.toLowerCase
    TableQuery[ProjectEntryRow]
      .distinctOn(_.user)
      .filter(_.user.toLowerCase.startsWith(lowerCasePrefix))
      .filterNot(_.user.toLowerCase like s"%|%")
      .groupBy(_.user).map(_._1)
      .take(limit)
      .result
  }


  /**
    * Returns a list of distinct obituaries starting with the given prefix
    * @param prefix restricts results to only obits starting with this string
    * @param limit only return up to this many results
    * @param db implicitly provided database object
    * @return a Future, containing a sequence of matching usernames
    */
  def listObits(prefix:String, limit:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = db.run {
    val lowerCasePrefix = prefix.toLowerCase
    TableQuery[ProjectEntryRow]
      .filter(_.isObitProject.isDefined)
      .distinctOn(_.isObitProject.get)
      .filter(_.isObitProject.toLowerCase like s"%$lowerCasePrefix%")
      .groupBy(_.isObitProject).map(_._1)
      .take(limit)
      .result
  }

  /**
    * Checks if the provided "uname" string is an exact, case-insensitive match for one that already exists in the database
    * @param uname username to test
    * @param db implicitly provided database object
    * @return a Future, containing True if at least one user matches and False otherwise
    */
  def isUserKnown(uname:String)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = db.run {
    val testValue = uname.toLowerCase
    TableQuery[ProjectEntryRow]
      .distinctOn(_.user)
      .filter(_.user.toLowerCase === testValue)
      .groupBy(_.user).map(_._1)
      .length
      .result
  }.map(count=>count>0)

  def scanProjectsForType(typeId:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    Source.fromPublisher(
      db.stream(TableQuery[ProjectEntryRow].filter(_.projectType===typeId).result)
    )
  }
}
