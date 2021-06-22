package services.actors

import akka.actor.Actor
import models.{AuditAction, AuditLog, AuditLogRow}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Auditor {
  trait AuditMsg

  case class LogEvent(username:String, actionType:AuditAction.Value, targetObjectId:Int, at:ZonedDateTime, data:Option[String]) extends AuditMsg
  case object PurgeOldLogs extends AuditMsg
}

@Singleton
class Auditor @Inject() (dbConfigProvider: DatabaseConfigProvider, config:Configuration) extends Actor {
  import Auditor._
  private val logger = LoggerFactory.getLogger(getClass)

  val db = dbConfigProvider.get[PostgresProfile].db

  override def receive: Receive = {
    case LogEvent(username, actionType, targetObjectId, timestamp, data)=>
      val newRecord = AuditLog(
        None,
        username = username.toLowerCase,
        actionType = actionType,
        targetObjectId = targetObjectId,
        at = Timestamp.from(timestamp.toInstant),
        data = data
      )

      db.run(
        TableQuery[AuditLogRow] += newRecord
      ).recover({
        case err:Throwable=>
          logger.error(s"Could not record new audit record $newRecord: ${err.getMessage}", err)
      })

    case PurgeOldLogs=>
      config.getOptional[FiniteDuration]("audit.purgeAfter") match {
        case None=>
          logger.warn("Could not purge audit logs table because audit.purgeAfter is not configured")
        case Some(purgeAfter)=>
          val nowtime = ZonedDateTime.now()
          val threshold = nowtime.minus(purgeAfter.toSeconds, ChronoUnit.SECONDS)
          logger.info(s"Purging audit logs that occurred before $threshold")

          db.run(
            TableQuery[AuditLogRow]
              .filter(_.at < Timestamp.from(threshold.toInstant))
              .delete
          ).onComplete({
            case Success(rowsDeleted)=>
              logger.info(s"Audit log purge completed, removed $rowsDeleted rows")
            case Failure(err)=>
              logger.warn(s"Could not purge the audit log table: ${err.getMessage}", err)
          })
      }
  }
}
