import java.io._

import collection.JavaConverters._
import org.scalatest._
import org.scalatest.MustMatchers._
import org.scalatest.Matchers._
import org.apache.pdfbox.rendering._
import org.apache.pdfbox.pdmodel._
import io.github.qwefgh90.akka.pdf._
import java.nio.file._
import java.net._
abstract class UnitSpec extends FlatSpec with Matchers with OptionValues with Inside with Inspectors

class PDFSpec extends UnitSpec {
  
  def using[R <: Closeable, T](stream: R)(f: R => T): T = try { f(stream) } finally { stream.close() }

  "PDF" should "removeEmptyPages" in {
    val htmlSource = "file://" + getClass.getResource("/emptypage.pdf").getPath;
    val htmlSourcePath = Paths.get(new URI(htmlSource))
    val htmlDestPath = Paths.get(new URI(htmlSource)).getParent.resolve("emptypage_copy.pdf")
    Files.deleteIfExists(htmlDestPath)
    Files.copy(htmlSourcePath, htmlDestPath)
    PdfUtil.removeEmptyPages(htmlDestPath.toAbsolutePath.toString)
    val document = PDDocument.load(new File(htmlDestPath.toAbsolutePath.toString))
    assert(document.getNumberOfPages == 2)
    document.close()
  }

  "PDF" should "htmlToPdf" in {
    val htmlSource = "file://" + getClass.getResource("/a4.html").getPath;
    val htmlSourcePath = Paths.get(new URI(htmlSource))
    val parent = htmlSourcePath.getParent
    val targetPath = parent.resolve("test.pdf")
    Files.deleteIfExists(targetPath)
    println("html: " + htmlSource)
    println("target: " + targetPath.toString)
    val processOpt = PdfUtil.htmlToPdf(htmlSource, targetPath.toAbsolutePath.toString)
    processOpt should not be empty
    Files.size(targetPath) should be > 0L
    targetPath.toFile.exists should be (true)
  }

  "PDF" should "merged" in {
    val flist = scala.collection.mutable.Buffer[InputStream](getClass.getResourceAsStream("/test1.pdf"), getClass.getResourceAsStream("/test2.pdf"))
    val is = PDFUtilWrapper.merge(flist.asJava)
    val length = is.available()
    length should be > 0
  }
}
