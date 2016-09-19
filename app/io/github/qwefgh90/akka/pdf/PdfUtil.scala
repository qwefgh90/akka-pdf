package io.github.qwefgh90.akka.pdf

import java.io._
import java.nio.file._
import org.apache.pdfbox.pdmodel.PDDocument

object PdfUtil{
  def htmlToPdf(uri: String, destPath: String): Option[Process] = {
    val phantomjs = getClass.getResource("/phantomjs.exe").toString.substring(6)
    val rasterize = getClass.getResource("/rasterize.js").toString.substring(6)
    if(Files.exists(Paths.get(phantomjs)) && Files.exists(Paths.get(rasterize))){
      val command = Array[String](phantomjs, rasterize, uri, destPath, "210mm*297mm")
      val process = Runtime.getRuntime().exec(command)
      process.waitFor()
      if(Files.exists(Paths.get(destPath))){
        removeLast(destPath)//last page which is empty will be deleted!
        Some(process)
      }
      else
        None
    }else
      None
  }

  def removeLast(path: String) {
    using(PDDocument.load(new File(path))){ document =>
      val last = document.getNumberOfPages - 1
      document.removePage(last)
      document.save(path)
    }
  }
  
  /*
   * input and output will be closed.
   */
  def readAndWrite(is: InputStream, os: OutputStream){
    using(is){ is =>
      using(os){ os =>
        Stream.continually(is.read()).takeWhile(_ != -1).map(_.toByte).foreach(os.write(_))
      }
    }
  }

  def using[R <: Closeable, T](stream: R)(f: R => T): T = try { f(stream) } finally { stream.close() }

}
