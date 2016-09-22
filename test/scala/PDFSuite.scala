import java.io._

import collection.JavaConverters._
import org.scalatest._
import io.github.qwefgh90.akka.pdf._
import java.nio.file._
import java.net._
abstract class UnitSpec extends FlatSpec with Matchers with OptionValues with Inside with Inspectors

class PDFSpec extends UnitSpec {
  
  def using[R <: Closeable, T](stream: R)(f: R => T): T = try { f(stream) } finally { stream.close() }
  "PDF" should "htmlToPdf" in {
    val htmlSource = "file://" + getClass.getResource("/a4.html").getPath;
    val htmlSourcePath = Paths.get(new URI(htmlSource))
    val parent = htmlSourcePath.getParent
    val targetPath = parent.resolve("test.pdf")
    targetPath.toFile.delete()
    println("html: " + htmlSource)
    println("target: " + targetPath.toString)
    val processOpt = PdfUtil.htmlToPdf(htmlSource, targetPath.toAbsolutePath.toString)
    processOpt should not be empty
    val process = processOpt.get
    using(process.getInputStream){ stream =>
      val buf = new StringBuilder
      Stream.continually(stream.read()).takeWhile(_ != -1).map(_.toChar).foreach(buf += _)
      println(buf.toString)
    }
    assert(targetPath.toFile.exists == true)
  }
  "PDF" should "merged" in {
    val flist = scala.collection.mutable.Buffer[InputStream](new FileInputStream("c:/Users/ChangChang/Documents/test3.pdf"), new FileInputStream("c:/Users/ChangChang/Documents/test4.pdf"))
    val is = PDFUtilWrapper.merge(flist.asJava)
    val length = is.available()
    assert(length > 0)
    val file = new File("c:/Users/ChangChang/Documents/result.pdf")
    val fo = new FileOutputStream(file)
    using(is){is => {
      using(fo){fo => {
        println("len: " + length)
        Stream.continually(is.read()).takeWhile(_ != -1).map(_.toByte).foreach(fo.write(_))
      }
      }
    }
    }
    assertResult(true){
      file.exists()
    }
   //file.delete()
  }
}
