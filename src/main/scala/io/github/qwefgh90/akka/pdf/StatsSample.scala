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
import io.github.qwefgh90.akka.pdf.PDFWorker._

object StatsSample {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup(Seq("2551", "2552", "0"))
      StatsSampleClient.main(Array.empty)
    } else {
      startup(args)
    }
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port when specified as program argument
      val config =
        ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + port).withFallback(
          ConfigFactory.parseString("akka.cluster.roles = [compute]")).
          withFallback(ConfigFactory.load("stats1"))

      val system = ActorSystem("ClusterSystem", config)

      
      system.actorOf(Props[PDFWorker], name = "pdfWorker")
      //      system.actorOf(Props[StatsService], name = "statsService")
    }
  }
}

object StatsSampleClient {
  def main(args: Array[String]): Unit = {
    // note that client is not a compute node, role not defined

    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + 0).withFallback(
      ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
      withFallback(ConfigFactory.load("stats1"))
    
    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props(classOf[StatsSampleClient]), name="serviceActor")
  }
}

class StatsSampleClient extends Actor {
  val cluster = Cluster(context.system)
  val workerRouter = context.actorOf(FromConfig.props(Props[PDFWorker]), name = "workerRouter")

  import context.dispatcher
  val tickTask = context.system.scheduler.schedule(2.seconds, 2.seconds, self, "tick")
  context.system.scheduler.schedule(10.seconds, 10.seconds, self, "poison")
  var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    tickTask.cancel()
  }

  def receive = {
    case "poison" =>{
      println("send poisonPill")
      workerRouter ! Broadcast(PoisonPill)
    }
    case "tick"  =>{
    
      println("tell tick: " + workerRouter.toString)
      workerRouter.tell("test", self)
      workerRouter ! MergeJob(FilePDFList(List(FilePDF("c:/Users/ChangChang/Documents/test1.pdf"), FilePDF("c:/Users/ChangChang/Documents/test2.pdf"))))
    }
    case result: String => println(result)
    case result: StatsResult =>
      println(result)
    case failed: JobFailed =>
      println(failed)
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up => m.address
      }
    case MemberUp(m) if m.hasRole("compute") =>{
      nodes += m.address
      println("memberUp: " + m.toString)
    }
    case other: MemberEvent =>{
      nodes -= other.member.address
      println("other: " + other.toString)
    }  
    case UnreachableMember(m) =>{
      nodes -= m.address
      println("UnreachableMember: " + m.toString)

    }  
    case ReachableMember(m) if m.hasRole("compute") =>
      {
      nodes += m.address
      println("ReachableMember: " + m.toString)
      }
    case _ => println("other receive")

  }
}
