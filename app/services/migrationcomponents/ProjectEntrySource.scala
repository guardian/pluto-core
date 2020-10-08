package services.migrationcomponents

import akka.stream.Outlet
import models.{ProjectEntry, ProjectEntryRow}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class ProjectEntrySource(dbConfig: DatabaseConfigProvider, internalBufferSize: Int)
  extends DBObjectSource[ProjectEntry](dbConfig, internalBufferSize) {

  override protected val out: Outlet[ProjectEntry] = Outlet.create("ProjectEntrySource.out")

  override def getNextPage(recordsRead: Int)(implicit db: JdbcBackend#DatabaseDef): Future[Seq[ProjectEntry]] = db.run {
    TableQuery[ProjectEntryRow].drop(recordsRead).take(internalBufferSize).sortBy(_.id).result
  }
}
