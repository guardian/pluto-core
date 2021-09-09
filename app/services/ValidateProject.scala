package services

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge, RunnableGraph, Sink}

import javax.inject.{Inject, Singleton}
import models.{FileEntryRow, ProjectEntry, ProjectEntryRow, ValidationJob, ValidationJobDAO, ValidationJobStatus, ValidationJobType, ValidationProblem, ValidationProblemDAO}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.lifted.{AbstractTable, Rep, TableQuery}
import streamcomponents.{FileValidationComponent, FindMislinkedProjectsComponent, FindUnlinkedProjects, GeneralValidationComponent, ProjectSearchSource, ProjectValidationComponent}
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ValidateProject {
  trait VPMsg

  /* public messages that are expected to be received */
  case class RequestValidation(job:ValidationJob) extends VPMsg

  /* public messages that will be sent in reply */
  case class ValidationSuccess(jobId:UUID) extends VPMsg
  case class ValidationError(err:Throwable) extends VPMsg
}

@Singleton
class ValidateProject @Inject()(config:Configuration,
                                dbConfigProvider:DatabaseConfigProvider,
                                validationJobDAO:ValidationJobDAO,
                                validationProblemDAO:ValidationProblemDAO)(implicit mat:Materializer, injector:Injector) extends Actor {
  import ValidateProject._
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * build a stream to perform the validation.
    * this consists of a database search as a source, a number of parallel verification threads and a folder to collect
    * up all records that failed.
    * @param parallelism
    * @param queryFunc
    * @return
    */
  def buildStream[E <:AbstractTable[_]](switcherFactory:GeneralValidationComponent[E], parallelism:Int=4, batchSize:Int=20)(queryFunc: Query[E, E#TableElementType, Seq]) = {
    val sinkFactory = Sink.foreach[Seq[ValidationProblem]](validationProblemDAO.batchInsertIntoDb)

    type T = E#TableElementType

    GraphDSL.create(sinkFactory) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      val src = builder.add(new ProjectSearchSource(dbConfigProvider)(queryFunc))
      val distrib = builder.add(Balance[T](parallelism))
      val noMerge = builder.add(Merge[ValidationProblem](parallelism))
      val batcher = builder.add(Flow[ValidationProblem].grouped(batchSize))

      src.out.log("services.ValidateProject") ~> distrib

      for(i<- 0 until parallelism){
        val switcher = builder.add(switcherFactory)
        distrib.out(i) ~> switcher ~> noMerge
      }

      noMerge ~> batcher ~> sink
      ClosedShape
    }
  }


  /**
    * runs the validation stream with the given database query
    *
    * @param switcherFactory Akka flow constructor class for the validator. This must receive data from the data type of the
    *                        given TableQuery and output ValidationProblem reports
    * @param parallelism     number of validations to run concurrently
    * @param queryFunc       Slick query that determines the data source to validate, e.g. `TableQuery[ProjectEntryRow]`
    * @tparam E              Data type of the Slick table that is being queried e.g. `ProjectEntryRow`.
    *                        This can be inferred from the `queryFunc` and `switcherFactory` arguments
    * @return                A Future, which completes with no value when the stream finishes
    */
  def runStream[E <:AbstractTable[_]](switcherFactory:GeneralValidationComponent[E], parallelism:Int=4)(queryFunc:Query[E, E#TableElementType, Seq]) =
    RunnableGraph.fromGraph(buildStream(switcherFactory, parallelism)(queryFunc)).run()

  /**
    * return the total number of records matching the query via a `SELECT COUNT` query
    * @param queryFunc Slick query that determines the data source to validate, e.g. `TableQuery[ProjectEntryRow]`
    * @tparam E              Data type of the Slick table that is being queried e.g. `ProjectEntryRow`.
    *                        This can be inferred from the `queryFunc` and `switcherFactory` arguments
    * @return                A Future, which completes with an integer representing the nmber of matching rows
    */
  def getTotalCount[E <:AbstractTable[_]](queryFunc: Query[E, E#TableElementType, Seq]) = {
    val db = dbConfigProvider.get.db

    db.run(queryFunc.size.result)
  }

  /**
    * performs validation by checking the total count of projects matching the query and running the verification
    * stream and returns the result as a ValidationSuccess object in a Future.
    * If the operation fails, then the future fails; catch this with .recover()
    *
    * @param switcherFactory Akka flow constructor class for the validator. This must receive data from the data type of the
    *                        given TableQuery and output ValidationProblem reports
    * @param parallelism     number of validations to run concurrently
    * @param queryFunc       Slick query that determines the data source to validate, e.g. `TableQuery[ProjectEntryRow]`
    * @tparam E              Data type of the Slick table that is being queried e.g. `ProjectEntryRow`.
    *                        This can be inferred from the `queryFunc` and `switcherFactory` arguments
    * @return                A Future, which completes once the validation is done, returning an integer of the number of
    *                        records queried as determined by a `SELECT COUNT` carried out at the start of the validation run
    */
  def performValidation[E <:AbstractTable[_]](switcherFactory:GeneralValidationComponent[E], parallelism:Int=4)(queryFunc: Query[E, E#TableElementType, Seq]) = {
    for {
      c <- getTotalCount(queryFunc)
      r <- runStream(switcherFactory)(queryFunc)
    } yield c
  }

  def runRequestedValidation(job:ValidationJob):Future[Int] = {
    job.jobType match {
      case ValidationJobType.CheckAllFiles=>
        performValidation(new ProjectValidationComponent(dbConfigProvider, job))(TableQuery[ProjectEntryRow])
      case ValidationJobType.CheckSomeFiles=>
        Future.failed(new RuntimeException("CheckSomeFiles has not been implemented yet"))
      case ValidationJobType.MislinkedPTR=>
        performValidation(new FindMislinkedProjectsComponent(dbConfigProvider, job))(TableQuery[ProjectEntryRow])
      case ValidationJobType.UnlinkedProjects=>
        performValidation(new FindUnlinkedProjects(dbConfigProvider, job))(TableQuery[ProjectEntryRow])
      case ValidationJobType.UnlinkedFiles=>
        performValidation(new FileValidationComponent(dbConfigProvider, job))(TableQuery[FileEntryRow].filter(_.hasContent===true))
      case ValidationJobType.UnlinkedFilesWithBlanks=>
        performValidation(new FileValidationComponent(dbConfigProvider, job))(TableQuery[FileEntryRow])
    }
  }

  override def receive: Receive = {
    case RequestValidation(job:ValidationJob)=>
      val originalSender = sender()
      logger.info(s"${job.uuid}: Received validation request for ${job.jobType} from ${job.userName}")
      val result = for {
        inProgressJob <- validationJobDAO.writeJob(job.copy(status = ValidationJobStatus.Running, startedAt = Some(Timestamp.from(Instant.now()))))
        validationResult <- runRequestedValidation(inProgressJob)
      } yield (inProgressJob, validationResult)

      result.map(result=>{
        logger.info(s"${job.uuid}: Validation completed successfully, processed ${result._2} records")
        validationJobDAO.setJobCompleted(job)
      }).onComplete({
        case Success(_)=>
          originalSender ! ValidationSuccess(job.uuid)
        case Failure(err)=>
          val failedJob = job.copy(status=ValidationJobStatus.Failure, completedAt=Some(Timestamp.from(Instant.now())))
          validationJobDAO.writeJob(failedJob).onComplete({
            case Success(_)=>()
            case Failure(err)=>
              logger.error(s"${job.uuid}: Could not write validation job failure to the database: ${err.getMessage}", err)
          })
          logger.error(s"${job.uuid}: Validation failed: ${err.getMessage}", err)
      })
  }
}
