package models
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PremiereVersionTranslationDAO @Inject() (dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext) {
  private final val logger = LoggerFactory.getLogger(getClass)
  private final val db = dbConfigProvider.get[PostgresProfile].db

  import PremiereVersionTranslationMappers._  //allows us to implicitly convert DisplayedVersion objects

  /**
    * Finds a version translation record for the given internal version number
    * @param internalVersion numeric internal premiere version number
    * @return a Future containing either the matching record or None if no record exists
    */
  def findInternalVersion(internalVersion:Int) = db.run {
    TableQuery[PremiereVersionTranslationRow].filter(_.internalVersionNumber===internalVersion).result
  }.map(_.headOption)

  /**
    * Finds any matching records for the given "displayed version", i.e. the one you see on the Mac
    * @param displayedVersion DisplayedVersion object representing the version. Get this by running DisplayedVersion(versionString),
    *                         and handle any parsing error from that before putting into this function
    * @return a Future containing a list of matching records or an empty list if no record exists
    */
  def findDisplayedVersion(displayedVersion: DisplayedVersion) = db.run {
    TableQuery[PremiereVersionTranslationRow].filter(_.displayedVersion===displayedVersion).result
  }

  /**
    * Writes the given record into the table, updating an existing record if there is one or creating a new one otherwise
    * @param record PremiereVersionTranslation to write
    * @return a Future with the number of records affected
    */
  def save(record:PremiereVersionTranslation) = db.run {
    TableQuery[PremiereVersionTranslationRow].filter(_.internalVersionNumber===record.internalVersionNumber).length.result
  }.flatMap(count=>{
    if(count==0) {
      db.run { TableQuery[PremiereVersionTranslationRow] += record }
    } else {
      db.run { TableQuery[PremiereVersionTranslationRow].filter(_.internalVersionNumber===record.internalVersionNumber).update(record) }
    }
  })

  /**
    * Removes the given internal id from the table.
    * @param internalId id to remove
    * @return a Future with the number of records affected
    */
  def remove(internalId:Int) = db.run {
    TableQuery[PremiereVersionTranslationRow].filter(_.internalVersionNumber===internalId).delete
  }
}
