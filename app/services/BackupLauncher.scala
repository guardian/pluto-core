package services
import com.google.inject.Guice
import play.api.inject.guice.GuiceApplicationBuilder
import services.guice.InjectionConfig

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object BackupLauncher {
  def main(args:Array[String]):Unit = {
    val app = new GuiceApplicationBuilder().build()
    implicit val injector = app.injector
    val projectBackup = injector.instanceOf(classOf[NewProjectBackup])

    projectBackup.backupProjects.onComplete({
      case Success(r)=>
          println(s"Out of ${r.totalCount} total, ${r.successCount} were backed up, ${r.failedCount} failed and ${r.notNeededCount} did not need backup")
        System.exit(0)
      case Failure(exception)=>
        println(s"ERROR - Could not complete backup: ${exception.getMessage}")
        exception.printStackTrace()
        System.exit(1)
    })
  }
}
