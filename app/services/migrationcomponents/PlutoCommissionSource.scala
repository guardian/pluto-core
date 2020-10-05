package services.migrationcomponents

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{PlutoCommission, PlutoCommissionRow}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class PlutoCommissionSource(dbConfig:DatabaseConfigProvider, internalBufferSize:Int) extends GraphStage[SourceShape[PlutoCommission]] {
  private final val logger = LoggerFactory.getLogger(getClass)
  private final val out:Outlet[PlutoCommission] = Outlet.create("PlutoCommissionSource.out")

  override def shape: SourceShape[PlutoCommission] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var internalBuffer:scala.collection.Seq[PlutoCommission] = Seq()
    private var recordsRead = 0
    private val db = dbConfig.get[PostgresProfile].db

    def getNextPage() = {
      db.run {
        TableQuery[PlutoCommissionRow].drop(recordsRead).take(internalBufferSize).sortBy(_.id).result
      }
    }

    val pushNextRecordCb = createAsyncCallback[Unit](_=>internalBuffer.headOption match {
      case Some(rec)=>
        this.synchronized {
          internalBuffer = internalBuffer.tail
        }
        push(out, rec)
      case None=>
        logger.info("Scanned out all commissions")
        complete(out)
    })

    val errCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        internalBuffer.headOption match {
          case Some(_)=>
            pushNextRecordCb.invoke( () )
          case None=>
            getNextPage().onComplete({
              case Success(newRecords)=>
                this.synchronized {
                  internalBuffer = internalBuffer ++ newRecords
                  recordsRead += newRecords.length
                }
                pushNextRecordCb.invoke(())
              case Failure(err)=>
                logger.error("Could not retrieve more records: ", err)
                errCb.invoke(err)
            })
        }
      }
    })
  }
}
