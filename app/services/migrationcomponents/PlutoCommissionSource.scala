package services.migrationcomponents

import akka.stream.Outlet
import models.{PlutoCommission, PlutoCommissionRow}
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.Future

class PlutoCommissionSource(dbConfig:DatabaseConfigProvider, internalBufferSize:Int)
  extends DBObjectSource[PlutoCommission](dbConfig, internalBufferSize)
{
    override protected val out:Outlet[PlutoCommission] = Outlet.create("PlutoCommissionSource.out")

    override protected def getNextPage(recordsRead:Int)(implicit db:PostgresProfile#Backend#Database): Future[Seq[PlutoCommission]] = db.run {
      TableQuery[PlutoCommissionRow].drop(recordsRead).take(internalBufferSize).sortBy(_.id).result
    }
}
