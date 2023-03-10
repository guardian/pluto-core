package services

import java.io.File
import java.sql.Timestamp

import akka.actor.{Actor, ActorSystem}
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import models.PostrunAction
import java.time.{Instant, ZonedDateTime}

import org.reflections.Reflections
import org.reflections.scanners.{ResourcesScanner, SubTypesScanner}
import org.reflections.util.{ClasspathHelper, ConfigurationBuilder, FilterBuilder}
import org.slf4j.MDC
import play.api.db.slick.DatabaseConfigProvider
import postrun.PojoPostrun
import slick.jdbc.PostgresProfile

import scala.jdk.CollectionConverters._
import collection.mutable._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object PostrunActionScanner {
  trait PASMsg

  case object Rescan extends PASMsg
}

@Singleton
class PostrunActionScanner @Inject() (dbConfigProvider: DatabaseConfigProvider, config:Configuration, actorSystem: ActorSystem) extends Actor {
  private val logger = Logger(this.getClass)
  import actorSystem.dispatcher

  implicit val db = dbConfigProvider.get[PostgresProfile].db
  implicit val configImplicit = config

  def scanPojos = {
    logger.info(s"URLs from classpath are ${ClasspathHelper.forPackage("postrun")}")

    val classLoadersList = ArrayBuffer(ClasspathHelper.contextClassLoader, ClasspathHelper.staticClassLoader)
    val reflections = new Reflections(new ConfigurationBuilder()
      .setScanners(new SubTypesScanner(false), new ResourcesScanner())
      .setUrls(ClasspathHelper.forPackage("postrun"))
    )

    reflections
      .getSubTypesOf(classOf[PojoPostrun])
      .asScala
      .foreach(classRef => addIfNotExists(classRef.getCanonicalName, s"java:${classRef.getCanonicalName}"))
  }

  protected def addIfNotExists(scriptName:String,absolutePath:String) = {
    logger.info(s"will add $scriptName at $absolutePath if it does not exist already")
    PostrunAction.entryForRunnable(absolutePath) map {
      case Success(results)=>
        if(results.isEmpty){
          logger.info(s"Adding newly found postrun script $absolutePath to database")
          val newRecord = PostrunAction(None,absolutePath,scriptName,None,"system",1,new Timestamp(ZonedDateTime.now().toEpochSecond*1000))
          newRecord.save map {
            case Failure(error)=>
              logger.error("Unable to save postrun script to database: ", error)
            case Success(newPostrunAction)=>
              logger.info(s"Saved postrun action for $scriptName with id of ${newPostrunAction.id.get}")
          }
        } else {
          logger.debug(s"Script $absolutePath is already present in database")
        }
      case Failure(error)=>
        logger.error("Could not look up script:", error)
    }
  }

  protected def addFileIfNotExists(scriptFile: File) = {
    addIfNotExists(scriptFile.getName, scriptFile.getName)
  }

  override def receive: Receive = {
    case PostrunActionScanner.Rescan=>
      logger.info("Rescanning postrun actions")

      scanPojos
  }

}
