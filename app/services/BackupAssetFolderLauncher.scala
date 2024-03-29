package services

import org.slf4j.LoggerFactory
import play.api.inject.guice.GuiceApplicationBuilder
import scopt.OptionParser
import services.guice.{BackupLauncherInjectionConfig, InjectionConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object BackupAssetFolderLauncher {
  private val logger = LoggerFactory.getLogger(getClass)

  case class Options(nukeInvalidBackups:Boolean, backupAll:Boolean)
  val parser = new OptionParser[Options]("backup-asset-folder-launcher") {
    head("backup-asset-folder-launcher", "1")

    opt[Boolean]("all") action { (x,c)=>c.copy(backupAll = x)} text "Try to back up every project instead of just 'in production'"
  }

  def main(args:Array[String]):Unit = {
    val app = new GuiceApplicationBuilder()
      .overrides(new BackupLauncherInjectionConfig)
      .disable(classOf[InjectionConfig])
      .build()
    implicit val injector = app.injector
    val projectBackup = injector.instanceOf(classOf[ProjectBackupAssetFolder])

    parser.parse(args, Options(false, false)) match {
      case Some(opts) =>
        projectBackup.backupProjects(!opts.backupAll).onComplete({
          case Success(r) =>
            logger.info(s"Attempt at backing up project files in asset folders completed.")
            System.exit(0)
          case Failure(exception) =>
            logger.error(s"ERROR - Could not complete backup: ${exception.getMessage}")
            exception.printStackTrace()
            System.exit(1)
        })
      case None =>
        System.exit(2)
    }
  }
}
