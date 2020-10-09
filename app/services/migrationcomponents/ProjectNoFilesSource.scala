package services.migrationcomponents

import akka.stream.Outlet
import models.{FileAssociationRow, ProjectEntry, ProjectEntryRow}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProjectNoFilesSource (dbConfig: DatabaseConfigProvider, internalBufferSize: Int)
  extends DBObjectSource[ProjectEntry](dbConfig, internalBufferSize) {
  override protected val out: Outlet[ProjectEntry] = Outlet.create("ProjectEntrySource.out")

  override def getNextPage(recordsRead: Int)(implicit db: JdbcBackend#DatabaseDef): Future[Seq[ProjectEntry]] = db.run {
    (TableQuery[ProjectEntryRow] joinFull TableQuery[FileAssociationRow] on (_.id===_.projectEntry))
      .filter(_._2.map(_.fileEntry).isEmpty)
      .drop(recordsRead)
      .take(internalBufferSize)
      .sortBy(_._1.map(_.id))
      .map(_._1)
      .result
  }.map(_.collect({case Some(project)=>project}))
}
