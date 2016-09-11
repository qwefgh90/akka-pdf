package io.github.qwefgh90.akka.pdf

import akka.actor.Actor
import akka.actor.PoisonPill
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.cluster.ClusterEvent._
import akka.actor.Address

import akka.event._
import io.github.qwefgh90.akka.pdf.PDFWorker._

//#worker
class PDFWorker extends Actor {
  val cluster = Cluster(context.system)
  val log = Logging(context.system, this)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    log.debug("ready to terminate");
    cluster.leave(cluster.selfAddress)
    context.system.terminate()
  }
  var nodes = Set.empty[Address]
  def receive = {
    case "test" => {
      log.info("test called");
      sender() ! "ok it's me"
    }
    case MergeJob(FilePDFList(pdfList)) => log.info(pdfList.toString)
    case MergeJob(MemoryPDFList(pdfList)) => log.info(pdfList.toString)
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up => m.address
      }

    case MemberUp(m) if m.hasRole("compute") =>{
      log.info("memberUp: " + m.toString)
    }
    case other: MemberEvent =>{
      log.info("other: " + other.toString)
    }  
    case UnreachableMember(m) =>{
      log.info("UnreachableMember: " + m.toString)

    }  
    case ReachableMember(m) if m.hasRole("compute") =>
      {
      log.info("ReachableMember: " + m.toString)
      }
    case _ =>
      log.info("other receive")
  }
}
//#worker

object PDFWorker {
  sealed trait Input
  sealed trait Output
  sealed trait ResultCode

  final case class FilePDF(path: String) extends Output
  final case class MemoryPDF(name: String, bytes: List[Byte]) extends Output

  final case class FilePDFList(pdfList: List[FilePDF]) extends Input
  final case class MemoryPDFList(pdfList: List[MemoryPDF]) extends Input
  
  final case class MergeJob(input: Input)
  final case class MergeResult(output: Output)

  final case class Suceess(msg: String) extends ResultCode
  final case class Fail(msg: String) extends ResultCode
}
