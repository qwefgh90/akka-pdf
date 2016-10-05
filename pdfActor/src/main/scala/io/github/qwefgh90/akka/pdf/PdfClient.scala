package io.github.qwefgh90.akka.pdf

import scala.concurrent.duration._
import java.util.concurrent.ThreadLocalRandom
import com.typesafe.config.ConfigFactory
import akka.routing.Broadcast
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.RelativeActorPath
import akka.actor.RootActorPath
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import akka.routing.FromConfig
import scala.concurrent.ExecutionContext.Implicits.global

import akka.event._
import io.github.qwefgh90.akka.pdf.PdfWorker._

object PdfClient {
/*  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + 0).withFallback(
      ConfigFactory.parseString("akka.cluster.roles = [frontend]"))
    .withFallback(ConfigFactory.load("akka-cluster"))
    .withFallback(ConfigFactory.load("stats1"))
    
    val system = ActorSystem("ClusterSystem", config)

    val actor = system.actorOf(Props(classOf[PdfClient]), name="serviceActor")
  }*/
}

class PdfClient extends Actor {
  val log = Logging(context.system, this)
  val cluster = Cluster(context.system)
  val workerRouter = context.actorOf(FromConfig.props(Props[PdfWorker]), name = "workerRouter")

  import context.dispatcher

  var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    cluster.leave(cluster.selfAddress)
    context.system.terminate()
  }

  def receive = {
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up => m.address
      }
    case MemberUp(m) if m.hasRole("compute") =>{
      nodes += m.address
      log.info("MemberUp: " + m.toString)
    }

    case UnreachableMember(m) =>{
      nodes -= m.address
      log.info("UnreachableMember: " + m.toString)

    }
    case ReachableMember(m) if m.hasRole("compute") =>{
      nodes += m.address
      log.info("ReachableMember: " + m.toString)
    }
    case message => {
      workerRouter forward message
    }
  }
}
