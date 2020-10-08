package services.migrationcomponents

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{PlutoCommission, PlutoCommissionRow, PlutoModel}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

abstract class DBObjectSource[T <: PlutoModel](dbConfig:DatabaseConfigProvider, internalBufferSize:Int) extends GraphStage[SourceShape[T]] {
  private final val logger = LoggerFactory.getLogger(getClass)
  protected val out:Outlet[T]

  override def shape: SourceShape[T] = SourceShape.of(out)

  protected def getNextPage(recordsRead:Int)(implicit db:PostgresProfile#Backend#Database):Future[Seq[T]]

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var internalBuffer:scala.collection.Seq[T] = Seq()
    private var recordsRead = 0
    private implicit val db = dbConfig.get[PostgresProfile].db

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
            getNextPage(recordsRead).onComplete({
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
