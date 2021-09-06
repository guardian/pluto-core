package models

import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.lifted.TableQuery

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
}
