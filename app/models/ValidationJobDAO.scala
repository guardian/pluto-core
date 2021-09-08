package models

import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class ValidationJobDAO @Inject() (dbConfigProvider:DatabaseConfigProvider) {
  import ValidationJobMappers._

  private val db = dbConfigProvider.get[PostgresProfile].db

  /**
    * writes the given ValidationJob record to the database.  If the `id` field is set then the existing record is updated,
    * if it is not then a new record is inserted.
    *
    * Returns the ValidationJob that was input, with the `id` field set if it was not set before
    * @param job ValidationJob to write to the database
    * @return a Future with the ValidationJob returned, the returned value will always have the `id`  field set correctly
    */
  def writeJob(job:ValidationJob) = {
    job.id match {
      case None=>
        db.run( (TableQuery[ValidationJobRow] returning TableQuery[ValidationJobRow].map(_.id)) += job)
          .map(newId=>job.copy(id=Some(newId)))
      case Some(existingId)=>
        db.run(TableQuery[ValidationJobRow].filter(_.id===existingId).update(job))
        .flatMap(rows=>{
          if(rows==0) {
            Future.failed(new RuntimeException("No rows were updated"))
          } else {
            Future(job)
          }
        })
    }
  }

  /**
    * update just the job status field, and optionally the completion timestamp
    * @param jobId job ID to update
    * @param newStatus the new status value
    * @param completionTimestamp optionally, a value to set for `completionTimestamp`
    * @return a Future with the updated row count for each query
    */
  def updateJobStatus(jobId:UUID, newStatus:ValidationJobStatus.Value, completionTimestamp:Option[Timestamp]=None) = {
    val queries = Seq(
      Some(TableQuery[ValidationJobRow].filter(_.uuid===jobId).map(_.status).update(newStatus)),
      completionTimestamp.map(ts=>
        TableQuery[ValidationJobRow].filter(_.uuid===jobId).map(_.completedAt).update(Some(ts))
      )
    )

    db.run(
      DBIO.sequence(queries.collect({case Some(q)=>q}))
    )
  }

  def setJobCompleted(job:ValidationJob) = {
    updateJobStatus(job.uuid, ValidationJobStatus.Success, Some(Timestamp.from(Instant.now())))
  }

  /**
    * retrieves the ValidationJob with the given UUID.  If no such job exists returns None
    * @param uuid uuid to look up
    * @return
    */
  def jobForUUID(uuid:UUID) = {
    db.run(
      TableQuery[ValidationJobRow].filter(_.uuid===uuid).result
    ).map(_.headOption)
  }

  /**
    * search for jobs with the given parameters. If no search parameters are given then will return everything.
    * @param userName username to search for, or None to ignore username
    * @param status status to search for, or None to ignore status
    * @param limit maximum number of results to return. Defaults to 100.
    * @return a list of the matching validation rows, or an empty sequence if nothing matches
    */
  def queryJobs(userName:Option[String]=None, status:Option[ValidationJobStatus.Value]=None, limit:Int=100) = {
    val baseQuery = TableQuery[ValidationJobRow]
    val userQuery = userName match {
      case None=>baseQuery
      case Some(user)=>baseQuery.filter(_.userName===user)
    }
    val statusQuery = status match {
      case None=>userQuery
      case Some(statusValue)=>userQuery.filter(_.status===statusValue)
    }

    for {
      hitCount <- db.run(statusQuery.length.result)
      results <- db.run(statusQuery.sortBy(_.startedAt.desc).take(limit).result)
    } yield (hitCount, results)
  }
}
