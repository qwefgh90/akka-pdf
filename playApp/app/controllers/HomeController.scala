package controllers

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.{ExecutionContext, Promise}

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.inject.ApplicationLifecycle
import play.api.Logger
import play.api.libs.json._

import scala.collection.mutable
import akka.pattern.gracefulStop
import akka.pattern.{ ask, pipe }
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.routing.FromConfig
import akka.actor.Props
import akka.actor.Actor
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import javax.inject._
import java.io._
import java.nio.file._

import services.PdfCache
import io.github.qwefgh90.akka.pdf._
import io.github.qwefgh90.akka.pdf.PdfWorker._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject() (conf: Configuration,lifecycle: ApplicationLifecycle, pdfCache: PdfCache) extends Controller {
  implicit val timeout = Timeout(conf.getInt("play.akka.timeout").get seconds)

  val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + 0).withFallback(
    ConfigFactory.parseString("akka.cluster.roles = [frontend]"))
    .withFallback(ConfigFactory.load("akka-cluster"))
    .withFallback(ConfigFactory.load("stats1"))
  
  val system = ActorSystem("ClusterSystem", config)
  val actor = system.actorOf(Props(classOf[PdfClient]), name="serviceActor")

  lifecycle.addStopHook { () =>
    val stopped: Future[Boolean] = gracefulStop(actor, 5 seconds)
    stopped
  }

  def WebToPdf = Action.async { request =>
    request.body.asFormUrlEncoded.map{form =>
      val uri = form("url")(0)
      Logger.debug(s"WebToPdf Request url: $uri")
      val future = actor.ask(HtmlToPdfMemoryJob(uri));
      future.mapTo[TransResult].map{result =>
        result match{
          case TransResult(Success(msg), Some(pdf: MemoryPdf)) => {
            val id: Int = scala.util.Random.nextInt
    	    val temp = File.createTempFile(System.currentTimeMillis.toString, id.toString);
            temp.deleteOnExit()
            val fos = new FileOutputStream(temp)
            fos.write(pdf.bytes)
            fos.close()
            pdfCache.put(id, temp.getAbsolutePath)
            Logger.info("WebToPdf Success temporary pdf path: " + temp.getAbsolutePath)
            Ok(Json.toJson(Map("link" -> ("/download/"+id))))
          }
          case v => {
            Logger.error("WebToPdf failed")
            Logger.error(v.toString)
            InternalServerError(v.toString)
          }
        }
      }
    }.getOrElse{
      Logger.error("WebToPdf invalid http request")
      Future(BadRequest)
    }
  }

  def MemoryMergeToMemory = Action.async(parse.multipartFormData) { request =>
    val files = request.body.files.sortWith((a,b) => a.key < b.key)
    val list = files.map{temp =>
      val path = temp.ref.file.toPath
      val bytes = Files.readAllBytes(path)
      Logger.info("MemoryMergeToMemory Request")
      Logger.info(s"multipart data ${temp.key} : " + temp.filename + ", " + bytes.length + " bytes")
      bytes
    };

    val future = actor ? MemoryMerge2MemoryJob(list.map{buf => ("", buf)}.toList);
    future.mapTo[MergeResult].map{result =>
      result match{
        case MergeResult(Success(msg), Some(pdf: MemoryPdf)) => {
          val id: Int = scala.util.Random.nextInt
    	  val temp = File.createTempFile(System.currentTimeMillis.toString, id.toString);
          temp.deleteOnExit()
          val fos = new FileOutputStream(temp)
          fos.write(pdf.bytes)
          fos.close()
          pdfCache.put(id, temp.getAbsolutePath)
          Logger.info("MemoryMergeToMemory Success temporary pdf path: " + temp.getAbsolutePath)
          Ok(Json.toJson(Map("link" -> ("/download/"+id))))
        }
        case result => {
          Logger.error("MemoryMergeToMemory failed")
          Logger.error(result.code.toString)
          InternalServerError(result.code.toString)
        }
      }
    }
  }

  def download(id: Int) = Action{
    val pathString = pdfCache.get(id)
    val buffer = new mutable.ArrayBuffer[Byte]
    val filePath = Paths.get(pathString)
    val bytes = Files.readAllBytes(filePath)
   /* val fis = new FileInputStream(path)
    try{
      Stream.continually(fis.read).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
    }finally{
      fis.close()
    }*/
    Ok(bytes).as("application/x-download").withHeaders(("Content-disposition", "attachment; filename="+ id.abs + ".pdf"))

  }

  def tick = Action.async {
    val future = actor ? "tick"
    future.mapTo[String].map{msg =>
      Ok(List(System.currentTimeMillis.toString, msg).mkString)}
  }

  def index = Action {
    Ok(views.html.index())
  }
}
