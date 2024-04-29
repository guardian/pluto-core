package models

import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import akka.stream.scaladsl.{Keep, Sink, Source}

@Singleton
class MissingAssetFileEntryDAO @Inject()(dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext, injector:Injector) {
  private final val db = dbConfigProvider.get[PostgresProfile].db
  private final val logger = LoggerFactory.getLogger(getClass)

  /**
    *  Writes this model into the database, inserting if id is None and returning a fresh object with id set. If an id
    * was set, then returns the same object. */
  def save(entry:MissingAssetFileEntry):Future[Try[MissingAssetFileEntry]] = entry.id match {
    case None=>
      val insertQuery = TableQuery[MissingAssetFileEntryRow] returning TableQuery[MissingAssetFileEntryRow].map(_.id) into ((item,id)=>item.copy(id=Some(id)))
      db.run(
        (insertQuery+=entry).asTry
      ).map({
        case Success(insertResult)=>Success(insertResult)
        case Failure(error)=>Failure(error)
      })
    case Some(realEntityId)=>
      db.run(
        TableQuery[MissingAssetFileEntryRow].filter(_.id===realEntityId).update(entry).asTry
      ).map({
        case Success(_)=>Success(entry)
        case Failure(error)=>Failure(error)
      })
  }

  def saveSimple(entry:MissingAssetFileEntry):Future[MissingAssetFileEntry] = save(entry).flatMap({
    case Success(e)=>Future(e)
    case Failure(err)=>Future.failed(err)
  })

  /**
    * Attempt to delete the underlying record from the database
    * @param entry MissingAssetFileEntry to delete
    * @return A Future with no value on success. On failure, the future fails; pick this up with .recover() or .onComplete
    */
  def deleteRecord(entry:MissingAssetFileEntry):Future[Unit] =
    entry.id match {
      case Some(databaseId)=>
        logger.info(s"Deleting database record for file $databaseId (${entry.filepath})")
        db.run(
          DBIO.seq(
            TableQuery[MissingAssetFileEntryRow].filter(_.id===databaseId).delete
          )
        )
      case None=>
        Future.failed(new RuntimeException("Cannot delete a record that has not been saved to the database"))
    }

    /**
    * Get a [[MissingAssetFileEntry]] instance for the given database id.
    * @param entryId Database id. to look up
    * @return A Future, containing an Option that may contain a [[MissingAssetFileEntry]] instance
    */
  def entryFor(entryId: Int):Future[Option[MissingAssetFileEntry]] =
    db.run(
      TableQuery[MissingAssetFileEntryRow].filter(_.id===entryId).result.asTry
    ).map({
      case Success(result)=>
        result.headOption
      case Failure(error)=>throw error
    })

  /**
   * Get a MissingAssetFileEntry instance for the given file path and project
   * @param filePath File path to search for
   * @param project Project to search for
   * @return A Future, containing a Try that contains a sequence of zero or more MissingAssetFileEntry instances
   */
  def entryFor(filePath: String, project:Int):Future[Try[Seq[MissingAssetFileEntry]]] =
    db.run(
      TableQuery[MissingAssetFileEntryRow]
        .filter(_.filepath===filePath)
        .filter(_.project===project)
        .result
        .asTry
    )
}

object MissingAssetFileEntryDAO extends ((Option[Int], Int, String)=>MissingAssetFileEntryDAO) {
  private final val logger = LoggerFactory.getLogger(getClass)

  def getRecords(project:Int)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) =
    db.run(
      TableQuery[MissingAssetFileEntryRow].filter(_.project===project).sortBy(_.filepath.asc).result.asTry
    )

  override def apply(v1: Option[Int], v2: Int, v3: String): MissingAssetFileEntryDAO = ???

  def loadAFilePerProject(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    Source.fromPublisher(db.stream(TableQuery[MissingAssetFileEntryRow].distinctOn(_.project).result))
  }
}
