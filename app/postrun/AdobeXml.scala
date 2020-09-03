package postrun
import java.io._
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.Elem

trait AdobeXml {
  def getGzippedInputStream(fileName: String):Try[GZIPInputStream] = Try {
      val rawInputStream = new FileInputStream(fileName)
      new GZIPInputStream(rawInputStream)
  }

  def getXmlFromGzippedFile(fileName:String):Try[Elem] =
    getGzippedInputStream(fileName).map(inputStream=>scala.xml.XML.load(inputStream))

  def getGzippedOutputStream(fileName: String):Try[GZIPOutputStream] = Try {
    val rawOutputStream = new FileOutputStream(fileName)
    new GZIPOutputStream(rawOutputStream)
  }

  def putXmlToGzippedFile(fileName:String, xmlData:Elem):Try[Unit] = {
    getGzippedOutputStream(fileName).map(outputStream=>{
      val writer = new OutputStreamWriter(outputStream)
      scala.xml.XML.write(writer,xmlData,"UTF-8",true,null)
      writer.flush()
      outputStream.finish()
    })
  }
}
