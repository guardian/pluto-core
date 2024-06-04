package services

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.concurrent.Future
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.stream.scaladsl.StreamConverters
import javax.inject.Inject

class ZipService @Inject()() {

  def zipProjectAndAssets(projectFilePath: String, assetFolderPath: String): Source[ByteString, _] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    StreamConverters.asOutputStream().mapMaterializedValue { os =>
      Future {
        val zipOut = new ZipOutputStream(os)
        try {
          // Add project file to zip
          val projectFile = Paths.get(projectFilePath)
          val projectFileEntry = new ZipEntry(projectFile.getFileName.toString)
          zipOut.putNextEntry(projectFileEntry)
          Files.copy(projectFile, zipOut)
          zipOut.closeEntry()

          // Add asset folder contents to zip
          def addFolderToZip(folder: File, parentFolder: String): Unit = {
            val files = folder.listFiles()
            if (files != null && files.nonEmpty) {
              files.foreach { file =>
                val entryName = parentFolder + "/" + file.getName
                if (file.isDirectory) {
                  addFolderToZip(file, entryName)
                } else {
                  zipOut.putNextEntry(new ZipEntry(entryName))
                  Files.copy(file.toPath, zipOut)
                  zipOut.closeEntry()
                }
              }
            } else {
              zipOut.putNextEntry(new ZipEntry(parentFolder + "/"))
              zipOut.closeEntry()
            }
          }

          val assetFolder = new File(assetFolderPath)
          addFolderToZip(assetFolder, assetFolder.getName)
        } finally {
          zipOut.close()
        }
      }
    }
  }
}