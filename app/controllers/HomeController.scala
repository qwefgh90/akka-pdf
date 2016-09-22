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

import services.PdfCache
import io.github.qwefgh90.akka.pdf._
import io.github.qwefgh90.akka.pdf.PdfWorker._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject() (lifecycle: ApplicationLifecycle, pdfCache: PdfCache) extends Controller {
  implicit val timeout = Timeout(5 seconds)

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



  def MemoryMergeToMemory = Action.async(parse.multipartFormData) { request =>
    val files = request.body.files.sortWith((a,b) => a.key < b.key)
    val list = files.map{temp =>
      val buffer = new mutable.ArrayBuffer[Byte]
      val fis = new FileInputStream(temp.ref.file)
      try{
        Stream.continually(fis.read).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
      }finally{
        fis.close()
      }
      Logger.info("input file: " + temp.filename + ", " + buffer.length)
      buffer.toArray
    };

    val future = actor ? MemoryMerge2MemoryJob(list.map{buf => ("", buf)}.toList);
    future.mapTo[MergeResult].map{result =>
      result match{
        case MergeResult(Success(msg), Some(pdf: MemoryPdf)) => {
          val id: Int = scala.util.Random.nextInt
    	  val temp = File.createTempFile(System.currentTimeMillis.toString, id.toString);
          val fos = new FileOutputStream(temp)
          fos.write(pdf.bytes)
          fos.close()
          pdfCache.put(id, temp.getAbsolutePath)
          Logger.info("savePath: " + temp.getAbsolutePath)
          Ok(Json.toJson(Map("link" -> ("/download/"+id))))
//          Ok(pdf.bytes).as("application/pdf")
        }
        case v => {
          Logger.info(v.toString)
          InternalServerError(v.toString)
        }
      }
    }
  }

  def MemoryMergeToFile = Action.async(parse.multipartFormData) { request =>
    val savePath = request.body.dataParts("savePath")(0)
    val files = request.body.files.sortWith((a,b) => a.key < b.key)
    val list = files.map{temp =>
      val buffer = new mutable.ArrayBuffer[Byte]
      val fis = new FileInputStream(temp.ref.file)
      try{
        Stream.continually(fis.read).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
      }finally{
        fis.close()
      }
      Logger.info("input file: " + temp.filename + ", " + buffer.length)
      buffer.toArray
    };

    val future = actor ? MemoryMerge2MemoryJob(list.map{buf => ("", buf)}.toList);
    future.mapTo[MergeResult].map{result =>
      result match{
        case MergeResult(Success(msg), Some(pdf: MemoryPdf)) => {
          Logger.info("savePath: " + savePath)
          val fos = new FileOutputStream(savePath)
          fos.write(pdf.bytes)
          fos.close()
          val id: Int = scala.util.Random.nextInt
          pdfCache.put(id, savePath)
          Ok(Json.toJson(Map("link" -> ("/download/"+id))))

        }
        case v => {
          Logger.info(v.toString)
          InternalServerError(v.toString)
        }
      }
    }
  }

  def FileMergeToFile = Action.async(parse.multipartFormData) { request =>
    val savePath = request.body.dataParts("savePath")(0)
    val fileMapToList = request.body.dataParts.toList.filter(_._1.startsWith("file")).sortWith((a,b) => a._1 < b._1)
    val fileList = fileMapToList.map{tuple2 => (tuple2._1, tuple2._2(0))}

    val list = fileList.map{temp =>
      val buffer = new mutable.ArrayBuffer[Byte]
      val fis = new FileInputStream(temp._2)
      try{
        Stream.continually(fis.read).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
      }finally{
        fis.close()
      }
      Logger.info("input file: " + temp._2 + ", " + buffer.length)
      buffer.toArray
    };

    val future = actor ? MemoryMerge2MemoryJob(list.map{buf => ("", buf)}.toList);
    future.mapTo[MergeResult].map{result =>
      result match{
        case MergeResult(Success(msg), Some(pdf: MemoryPdf)) => {
          Logger.info("savePath: " + savePath)
          val fos = new FileOutputStream(savePath)
          fos.write(pdf.bytes)
          fos.close()
          val id: Int = scala.util.Random.nextInt
          pdfCache.put(id, savePath)
          Ok(Json.toJson(Map("link" -> ("/download/"+id))))
        }
        case v => {
          Logger.info(v.toString)
          InternalServerError(v.toString)
        }
      }
    }
  }


  def FileMergeToMemory = Action.async(parse.multipartFormData) { request =>
    val fileMapToList = request.body.dataParts.toList.filter(_._1.startsWith("file")).sortWith((a,b) => a._1 < b._1)
    val fileList = fileMapToList.map{tuple2 => (tuple2._1, tuple2._2(0))}

    val list = fileList.map{temp =>
      val buffer = new mutable.ArrayBuffer[Byte]
      val fis = new FileInputStream(temp._2)
      try{
        Stream.continually(fis.read).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
      }finally{
        fis.close()
      }
      Logger.info("file: " + temp._2 + ", " + buffer.length)
      buffer.toArray
    };

    val future = actor ? MemoryMerge2MemoryJob(list.map{buf => ("", buf)}.toList);
    future.mapTo[MergeResult].map{result =>
      result match{
        case MergeResult(Success(msg), Some(pdf: MemoryPdf)) => {
          val id: Int = scala.util.Random.nextInt
    	  val temp = File.createTempFile(System.currentTimeMillis.toString, id.toString);
          val fos = new FileOutputStream(temp)
          fos.write(pdf.bytes)
          fos.close()
          pdfCache.put(id, temp.getAbsolutePath)
          Logger.info("savePath: " + temp.getAbsolutePath)
          Ok(Json.toJson(Map("link" -> ("/download/"+id))))
//          Ok(pdf.bytes).as("application/pdf")
        }
        case v => {
          Logger.info(v.toString)
          InternalServerError(v.toString)
        }
      }
    }
  }

  def download(id: Int) = Action{
    val path = pdfCache.get(id)
    val buffer = new mutable.ArrayBuffer[Byte]
    val fis = new FileInputStream(path)
    try{
      Stream.continually(fis.read).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
    }finally{
      fis.close()
    }
    Ok(buffer.toArray).as("application/x-download").withHeaders(("Content-disposition", "attachment; filename="+ id.abs + ".pdf"))

  }


  def tick = Action.async {
    val future = actor ? "tick"
    future.mapTo[String].map{msg =>
      Ok(List(System.currentTimeMillis.toString, msg).mkString)}
  }
  def index = Action {
    Ok(views.html.index())
  }
  def a4 = Action {
    Ok(views.html.test())
  }
}
