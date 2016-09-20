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

import io.github.qwefgh90.akka.pdf._
import io.github.qwefgh90.akka.pdf.PdfWorker._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject() (lifecycle: ApplicationLifecycle) extends Controller {
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
  def MemoryMergeToMemory = Action(parse.multipartFormData) { request => 
    val files = request.body.files.sortWith((a,b) => a.key < b.key)
    val list = files.map{temp => 
      val buffer = new mutable.ArrayBuffer[Byte]
      val fis = new FileInputStream(temp.ref.file)
      try{
        Stream.continually(fis.read).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
      }finally{
        fis.close()
      }
      buffer.toArray
    };

    val future = actor ? MemoryMerge2MemoryJob(list.map{buf => ("", buf)}.toList);
    future.mapTo[MergeResult].map{result =>
      result match{
        case MergeResult(Success(msg), Some(pdf)) => Logger.info(msg)
        case v => Logger.info(v.toString)
      }
    }

//    Logger.info("files: "+files.toString)
    Redirect("/")
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
