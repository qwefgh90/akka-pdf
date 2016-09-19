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

import io.github.qwefgh90.akka.pdf._

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
    val files = request.body.files
    val dataParts = request.body.dataParts
    Logger.info("\nname: " + dataParts("meta[name][firstname]"))
    Logger.info("\n" + dataParts.toString)
    val info = "\n" + files.map{file => "fileName: " + file.filename + ", contentType: " + file.contentType.get}.mkString("\n") 
    Logger.info(info)
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
