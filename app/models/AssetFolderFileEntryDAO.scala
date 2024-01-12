package models

import akka.stream.scaladsl.Source
import drivers.StorageDriver
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import java.nio.file.{Path, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AssetFolderFileEntryDAO @Inject()(dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext, injector:Injector) {
  private final val db = dbConfigProvider.get[PostgresProfile].db
  private final val logger = LoggerFactory.getLogger(getClass)

  /**
    *  Writes this model into the database, inserting if id is None and returning a fresh object with id set. If an id
    * was set, then returns the same object. */
  def save(entry:AssetFolderFileEntry):Future[Try[AssetFolderFileEntry]] = entry.id match {
    case None=>
      val insertQuery = TableQuery[AssetFolderFileEntryRow] returning TableQuery[AssetFolderFileEntryRow].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=entry).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult)
        case Failure(error)=>Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[AssetFolderFileEntryRow].filter(_.id===realEntityId).update(entry).asTry
      ).map({
        case Success(_)=>Success(entry)
        case Failure(error)=>Failure(error)
      })
  }

  def saveSimple(entry:AssetFolderFileEntry):Future[AssetFolderFileEntry] = save(entry).flatMap({
    case Success(e)=>Future(e)
    case Failure(err)=>Future.failed(err)
  })

  def storage(entry:AssetFolderFileEntry):Future[Option[StorageEntry]] = {
    db.run(
      TableQuery[StorageEntryRow].filter(_.id===entry.storageId).result
    ).map(_.headOption)
  }

  def getFullPath(entry:AssetFolderFileEntry):Future[String] =
    storage(entry).map({
      case Some(storage)=>
        Paths.get(storage.rootpath.getOrElse(""), entry.filepath).toString
      case None=>
        entry.filepath
    })

  /**
    * Attempt to delete the underlying record from the database
    * @param entry FileEntry to delete
    * @return A Future with no value on success. On failure, the future fails; pick this up with .recover() or .onComplete
    */
  def deleteRecord(entry:AssetFolderFileEntry):Future[Unit] =
    entry.id match {
      case Some(databaseId)=>
        logger.info(s"Deleting database record for file $databaseId (${entry.filepath})")
        db.run(
          DBIO.seq(
            TableQuery[AssetFolderFileEntryRow].filter(_.id===databaseId).delete
          )
        )
      case None=>
        Future.failed(new RuntimeException("Cannot delete a record that has not been saved to the database"))
    }

    /**
    * Get an [[AssetFolderFileEntry]] instance for the given database id.
    * @param entryId Database id. to look up
    * @return A Future, containing an Option that may contain a [[AssetFolderFileEntry]] instance
    */
  def entryFor(entryId: Int):Future[Option[AssetFolderFileEntry]] =
    db.run(
      TableQuery[AssetFolderFileEntryRow].filter(_.id===entryId).result.asTry
    ).map({
      case Success(result)=>
        result.headOption
      case Failure(error)=>throw error
    })

  /**
    * Get an AssetFolderFileEntry instance for the given filename and version
    * @param fileName File name to search for
    * @param version id. to search for
    * @return A Future, containing a Try that contains a sequence of zero or more AssetFolderFileEntry instances
    */
  def entryFor(fileName: String, version:Int):Future[Try[Seq[AssetFolderFileEntry]]] =
    db.run(
      TableQuery[AssetFolderFileEntryRow]
        .filter(_.filepath===fileName)
        .filter(_.version===version)
        .result
        .asTry
    )

  /**
    * Improved version of entryFor that returns either one or no entries in a more composable way.
    * This should be all that is needed because of table constraints
    * @param fileName The file name to search for (exact match)
    * @param version Version number to search for
    * @return A Future containing either an AssetFolderFileEntry or None. The future fails if there is a problem.
    */
  def singleEntryFor(fileName: String, storageId:Int, version:Int):Future[Option[AssetFolderFileEntry]] =
    db.run(
      TableQuery[AssetFolderFileEntryRow].filter(_.filepath===fileName).filter(_.storage===storageId).filter(_.version===version).result
    ).map(_.headOption)

  def allVersionsFor(fileName: String):Future[Try[Seq[AssetFolderFileEntry]]] =
    db.run(
      TableQuery[AssetFolderFileEntryRow].filter(_.filepath===fileName).sortBy(_.version.desc.nullsLast).result.asTry
    )


  /**
    * Returns a list of matching records for the given file name, ordered by most recent first (if versioning is enabled)
    * @param target File path to query. this should be a relative filepath for the given storage.
    * @return A Future containing a sequence of results
    */
  def findByFilename(target:Path, drop:Int=0, take:Int=100) = {
    val baseQuery = TableQuery[AssetFolderFileEntryRow].filter(_.filepath===target.toString)

    db.run {
      baseQuery.sortBy(_.version.desc.nullsLast).drop(drop).take(take).result
    }
  }

  /**
    * Returns a streaming source that lists out all files in the database, optionally limiting to a given storage id.
    * @return An Akka Source, that yields AssetFolderFileEntry objects
    */
  def scanAllFiles() = {
    val baseQuery = TableQuery[AssetFolderFileEntryRow]

    Source.fromPublisher(db.stream(baseQuery.sortBy(_.mtime.asc).result))
  }

  def validatePathExists(entry:AssetFolderFileEntry) =
    for {
      filePath <- getFullPath(entry)
      maybeStorage <- storage(entry)
      result <- Future(
        maybeStorage
          .map(_.validatePathExists(filePath, entry.version)) match {
          case Some(result)=>result
          case None=>Left(s"No storage could be found for id. ${entry.storageId} on file ${entry.id}")
        }
      )
    } yield result

  def validatePathExistsDirect(entry:AssetFolderFileEntry)(implicit driver:StorageDriver) = {
    getFullPath(entry).map(path=>driver.pathExists(path, entry.version))
  }

  def entryForLatestVersionByProject(projectId: Int, storageId: Int, filePath: String):Future[Option[AssetFolderFileEntry]] =
    db.run(
      TableQuery[AssetFolderFileEntryRow].filter(_.storage===storageId).filter(_.project===projectId).filter(_.filepath===filePath).sortBy(_.version.desc.nullsLast).result.asTry
    ).map({
      case Success(result)=>
        result.headOption
      case Failure(error)=>throw error
    })
}
