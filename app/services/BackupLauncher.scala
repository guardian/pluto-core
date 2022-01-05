package services
import services.guice.Module
import com.google.inject.Guice

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object BackupLauncher {
  def main(args:Array[String]):Unit = {
    implicit val injector = Guice.createInjector(new Module())

    val projectBackup = injector.getInstance(classOf[ProjectBackup])

    projectBackup.backupProjects.onComplete({
      case Success(backupResults)=>
        println(s"${backupResults.length} backups were performed.")
        backupResults.foreach(r=>println(s"\tStorage ${r.storageId}: Out of ${r.totalCount} total, ${r.successCount} were backed up, ${r.failedCount} failed and ${r.notNeededCount} did not need backup"))
        System.exit(0)
      case Failure(exception)=>
        println(s"ERROR - Could not complete backup: ${exception.getMessage}")
        exception.printStackTrace()
        System.exit(1)
    })
  }
}
