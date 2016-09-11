import java.io._

import collection.JavaConverters._
import org.scalatest._
import io.github.qwefgh90.akka.pdf._
abstract class UnitSpec extends FlatSpec with Matchers with OptionValues with Inside with Inspectors

class PDFSpec extends UnitSpec {
  
  def using[R <: Closeable, T](stream: R)(f: R => T): T = try { f(stream) } finally { stream.close() }

  "PDFs" should "merged" in {
    val flist = scala.collection.mutable.Buffer[InputStream](new FileInputStream("c:/Users/ChangChang/Documents/test1.pdf"), new FileInputStream("c:/Users/ChangChang/Documents/test2.pdf"))
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
