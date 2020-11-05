package services.migrationcomponents

import akka.stream.Outlet
import models.{ProjectEntry, ProjectEntryRow}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.Future

class ProjectEntryNullCommissionSource(dbConfig: DatabaseConfigProvider, internalBufferSize: Int)
  extends DBObjectSource[ProjectEntry](dbConfig, internalBufferSize) {

  override protected val out: Outlet[ProjectEntry] = Outlet.create("ProjectEntryNullCommissionSource.out")

  override def getNextPage(recordsRead: Int)(implicit db: JdbcBackend#DatabaseDef): Future[Seq[ProjectEntry]] = db.run {
    TableQuery[ProjectEntryRow].filter(_.commission.isEmpty).sortBy(_.id).drop(recordsRead).take(internalBufferSize).result
  }
}
