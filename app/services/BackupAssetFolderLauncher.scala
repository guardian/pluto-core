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

    opt[Boolean]("nuke-invalid") action { (x,c)=>c.copy(nukeInvalidBackups = x)} text("Instead of launching a backup, remove zero-length backup files")
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

        if (opts.nukeInvalidBackups) {
          projectBackup.nukeInvalidBackups.onComplete({
            case Success(r) =>
              println(s"Nuked ${r.successCount} dodgy backups")
              System.exit(0)
            case Failure(exception) =>
              println(s"ERROR - Could not complete scan: ${exception.getMessage}")
              exception.printStackTrace()
              System.exit(1)
          })
        } else {
          projectBackup.backupProjects(!opts.backupAll).onComplete({
            case Success(r) =>
              logger.info(s"Out of ${r.totalCount} total, ${r.successCount} were backed up, ${r.failedCount} failed and ${r.notNeededCount} did not need backup")
              System.exit(0)
            case Failure(exception) =>
              logger.error(s"ERROR - Could not complete backup: ${exception.getMessage}")
              exception.printStackTrace()
              System.exit(1)
          })
        }
      case None =>
        System.exit(2)
    }
  }
}
