package io.github.qwefgh90.akka.pdf

import java.awt._
import java.awt.image._
import java.io._
import java.nio.file._
import java.nio.file.attribute._
import java.util._
import org.apache.pdfbox.rendering._
import org.apache.pdfbox.pdmodel._
object PdfUtil{

	lazy val tempRasterizePath = {
		val temp = Files.createTempFile(System.currentTimeMillis.toString, "rasterize.js")
		val is = getClass.getResourceAsStream("/rasterize.js")
		try{
			Files.delete(temp)
			Files.copy(is, temp)
			temp.toFile.deleteOnExit()
			println("rasterize copied: " + temp.toString)
		}finally{
		  is.close()	
		}
		temp
	}
	lazy val tempPhantomjsPath = {
        val systemName = System.getProperty("os.name").toLowerCase;
		val temp = if(systemName == "linux") Files.createTempFile(System.currentTimeMillis.toString, "phantomjs") else Files.createTempFile(System.currentTimeMillis.toString, "phantomjs.exe")

		val is = if(systemName == "linux") getClass.getResourceAsStream("/phantomjs") else getClass.getResourceAsStream("/phantomjs.exe")
		try{
			Files.delete(temp)
			Files.copy(is, temp)
            temp.toFile.setExecutable(true)
			temp.toFile.deleteOnExit()
			println("phantomjs copied: " + temp.toString)
		}finally{
		  is.close()	
		}
		temp
	}
  /*
   * uri: URI format string
   * destPath: directory where pdf is created
   */
  def htmlToPdf(uri: String, destPath: String): Option[Process] = {
    //val phantomjs = getClass.getResource("/phantomjs.exe").toURI//.toString.substring(6)
    //val phantomjsPath = Paths.get(phantomjs)
	//val rasterize = getClass.getResource("/rasterize.js").toURI//.toString.substring(6)
    //val rasterizePath = Paths.get(rasterize)
	
	if(Files.exists(tempPhantomjsPath) && Files.exists(tempRasterizePath)){
      val command = Array[String](tempPhantomjsPath.toAbsolutePath.toString, tempRasterizePath.toAbsolutePath.toString, uri, destPath, "210mm*297mm")
      println("command: " + command.mkString(" "))
      val process = Runtime.getRuntime().exec(command)
      process.waitFor()
      if(Files.exists(Paths.get(destPath))){
        removeEmptyPages(destPath)//last page which is empty will be deleted!
        Some(process)
      }
      else
        None
    }else
      None
  }

  /*
   * remove final page
   */
  def removeLast(path: String) {
    using(PDDocument.load(new File(path))){ document =>
      val last = document.getNumberOfPages - 1
      if(document.getNumberOfPages > 1)
        document.removePage(last)
      document.save(path)
    }
  }

  def removeEmptyPages(path: String){
    using(PDDocument.load(new File(path))){ document =>
      val pdfRenderer = new PDFRenderer(document);
      var i = 0
      while(i < document.getNumberOfPages){
        val page: PDPage = document.getPage(i)
        val image = pdfRenderer.renderImage(i)
        if(isBlank(image)){
          println(s"removed $i page")
          document.removePage(i)
          i = i - 1
        }
        i += 1
      }
      document.save(path)
    }
  }

  def removeEmptyPages(bytes: Array[Byte]) = {
    val fo = new ByteArrayOutputStream()
    try{
      using(PDDocument.load(bytes)){ document =>
        val pdfRenderer = new PDFRenderer(document);
        var i = 0
        while(i < document.getNumberOfPages){
          val page: PDPage = document.getPage(i)
          val image = pdfRenderer.renderImage(i)
          if(isBlank(image)){
            println(s"removed $i page")
            document.removePage(i)
            i = i - 1
          }
          i += 1
        }
        document.save(fo)
      }
    }finally{
      fo.close()
    }
    fo.toByteArray
  }

  def isBlank(bufferedImage: BufferedImage) = {
    var count = 0;
    val height = bufferedImage.getHeight;
    val width = bufferedImage.getWidth;
    val areaFactor = (width * height) * 0.99;

    for (x <- 0 until width) {
        for (y <- 0 until height) {
          //println(s"width, height: $width, $height" + s"x,y:  $x, $y")
          val c = new Color(bufferedImage.getRGB(x, y))
          // verify light gray and white
          if (c.getRed() == c.getGreen() && c.getRed() == c.getBlue()
            && c.getRed() >= 248) {
            count += 1
          }
        }
    }

    if (count >= areaFactor) 
      true
    else
      false

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
