package io.github.qwefgh90.akka.pdf

import java.awt._
import java.awt.image._
import java.io._
import java.nio.file._
import java.nio.file.attribute._
import java.util._
import org.apache.pdfbox.rendering._
import org.apache.pdfbox.pdmodel._
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
object PdfUtil{
    val LOG = LogFactory.getLog(PdfUtil.getClass)
	lazy val tempRasterizePath = {
		val temp = Files.createTempFile(System.currentTimeMillis.toString, "rasterize.js")
		val is = getClass.getResourceAsStream("/rasterize.js")
		try{
			Files.delete(temp)
			Files.copy(is, temp)
			temp.toFile.deleteOnExit()
			LOG.info("rasterize copied: " + temp.toString)
		}finally{
		  is.close()	
		}
		temp
	}
	lazy val tempPhantomjsPath = {
        val systemName = System.getProperty("os.name").toLowerCase;
        val is64bits = System.getProperty("os.arch") == "amd64"
        if(!is64bits)
            throw new UnsupportedOperationException("This opration cannot be supported in 32bits OS")
		val temp = if(systemName == "linux") Files.createTempFile(System.currentTimeMillis.toString, "phantomjs") else if(systemName.contains("windows")) Files.createTempFile(System.currentTimeMillis.toString, "phantomjs.exe") else throw new UnsupportedOperationException("This opration cannot be supported in any other OS except Windows or Ubuntu")

		val is = if(systemName == "linux") getClass.getResourceAsStream("/phantomjs") else if(systemName.contains("windows")) getClass.getResourceAsStream("/phantomjs.exe") else throw new UnsupportedOperationException("This opration cannot be supported in any other OS except Windows or Ubuntu")
		try{
			Files.delete(temp)
			Files.copy(is, temp)
            temp.toFile.setExecutable(true)
			temp.toFile.deleteOnExit()
			LOG.info("phantomjs copied: " + temp.toString)
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
    Files.deleteIfExists(Paths.get(destPath))
	if(Files.exists(tempPhantomjsPath) && Files.exists(tempRasterizePath)){
      val command = Array[String](tempPhantomjsPath.toAbsolutePath.toString, tempRasterizePath.toAbsolutePath.toString, uri, destPath, "210mm*297mm")
      LOG.info("command: " + command.mkString(" "))
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


  def removeEmptyPages(path: String){
    using(PDDocument.load(new File(path))){ document =>
      val pdfRenderer = new PDFRenderer(document);
      var i = 0
      while(i < document.getNumberOfPages){
        val page: PDPage = document.getPage(i)
        val image = pdfRenderer.renderImage(i)
        if(isBlank(image)){
          LOG.info(s"removed $i page")
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
            LOG.info(s"removed $i page")
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
          val c = new Color(bufferedImage.getRGB(x, y))
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
