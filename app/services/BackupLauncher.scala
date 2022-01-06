package services
import play.api.inject.guice.GuiceApplicationBuilder
import scopt.OptionParser

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object BackupLauncher {
  case class Options(nukeInvalidBackups:Boolean)
  val parser = new OptionParser[Options]("backup-launcher") {
    head("backup-launcher", "1")

    opt[Boolean]("nuke-invalid") action { (x,c)=>c.copy(nukeInvalidBackups = x)} text("instead of launching a backup, remove zero-length backup files")
  }

  def main(args:Array[String]):Unit = {
    val app = new GuiceApplicationBuilder().build()
    implicit val injector = app.injector
    val projectBackup = injector.instanceOf(classOf[NewProjectBackup])

    parser.parse(args, Options(false)) match {
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
          projectBackup.backupProjects.onComplete({
            case Success(r) =>
              println(s"Out of ${r.totalCount} total, ${r.successCount} were backed up, ${r.failedCount} failed and ${r.notNeededCount} did not need backup")
              System.exit(0)
            case Failure(exception) =>
              println(s"ERROR - Could not complete backup: ${exception.getMessage}")
              exception.printStackTrace()
              System.exit(1)
          })
        }
      case None =>
        System.exit(2)
    }
  }
}
