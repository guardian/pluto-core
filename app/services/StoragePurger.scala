package services

import akka.stream.{ClosedShape, Materializer, SourceShape}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import models.{FileEntry, FileEntryRow}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery
import streamcomponents.DatabaseSearchSource
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class StoragePurger @Inject() (dbConfigProvider:DatabaseConfigProvider)(implicit injector:Injector, ec:ExecutionContext, mat:Materializer) {
  private val logger = LoggerFactory.getLogger(getClass)
  private implicit lazy val db = dbConfigProvider.get[PostgresProfile].db

  /**
    * try to remove, both from disk and from the database, files which are not marked as having content
    * @param storageId storage id to purge
    * @return
    */
  def purgeEmptyFiles(storageId:Int) = {
    val sinkFactory = Sink.ignore
    val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val query = TableQuery[FileEntryRow]
        .filter(_.storage===storageId)
        .filter(_.hasContent===false)

      val src = builder.add(new DatabaseSearchSource[FileEntryRow](dbConfigProvider)(query))

      src.out.mapAsync(2)(elem=>{
        if(elem.storageId!=storageId || elem.hasContent) {
          logger.error(s"Invalid record for storagepurger: $elem")
          throw new RuntimeException("Invalidn record received")
        } else {
          Future.sequence(Seq(
            elem.deleteFromDisk,
            elem.deleteSelf.map({
              case Left(err)=>Left(err.toString)
              case Right(_)=>Right(true)
            })
          )).map(results=>{
            results.head match {
              case Left(err)=>
                logger.error(s"Could not delete $elem from disk: $err")
              case Right(false)=>
                logger.warn(s"File for $elem was not deleted, see above logs for details")
              case Right(true)=>
                logger.info(s"Deleted file for $elem")
            }
            results(1) match {
              case Left(err)=>
                logger.error(s"Could not delete $elem from database: $err")
              case Right(_)=>
                logger.info(s"Delete db entry for $elem")
            }
          })
        }
      }) ~> sink
      ClosedShape
    }

    logger.info(s"Empty file purge starting on storage $storageId")
    val taskFuture = RunnableGraph.fromGraph(graph).run()
    taskFuture.onComplete({
      case Success(_)=>
        logger.info(s"Empty file purge completed on storage $storageId")
      case Failure(err)=>
        logger.error(s"Empty file purge failed for storage $storageId: ${err.getMessage}", err)
    })
    taskFuture
  }
}
