package models

import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.lifted.TableQuery

import java.util.UUID
import javax.inject.{Inject, Singleton}

@Singleton
class ValidationProblemDAO @Inject() (dbConfigProvider:DatabaseConfigProvider) {
  private val logger = LoggerFactory.getLogger(getClass)

  private implicit val db = dbConfigProvider.get[JdbcProfile].db

  /**
    * performs a batch insert of multiple ValidationProblem records.
    * Used by the ValidationProblem scanner
    * @param records list of records to add
    * @return  slick multiple insert result
    */
  def batchInsertIntoDb(records:Seq[ValidationProblem]) = db.run(
    TableQuery[ValidationProblemRow] ++= records
  )

  /**
    * search for all fault reports corresponding to the given job ID.
    *
    * Results are returned in timestamp order, with the oldest first; this should mean that pagination
    * is reliable.
    *
    * @param jobId job ID to search for
    * @param from record index to start with, for paginating queries
    * @param limit maximum number of records to return in one call
    * @return a Future containing a list of the reports.  If there is an error, the future fails.
    */
  def faultsForJobID(jobId:UUID, from:Int, limit:Int) = db.run(
    TableQuery[ValidationProblemRow]
      .filter(_.jobId===jobId)
      .sortBy(_.timestamp.asc)
      .drop(from)
      .take(limit)
      .result
  )

  def faultCountForJob(jobId:UUID) = db.run(
    TableQuery[ValidationProblemRow].filter(_.jobId===jobId).length.result
  )
}
