package io.github.qwefgh90.akka.pdf

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.cluster.ClusterEvent._
import akka.actor.Address
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory
import akka.event._
import collection.JavaConverters._
import scala.collection.mutable

import java.net._
import java.nio.file._
import java.io._
import io.github.qwefgh90.akka.pdf.PdfWorker._
import io.github.qwefgh90.akka.pdf.PdfUtil._

object PdfWorker {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup(Seq("2551", "2552"))
    } else {
      val (digitList, noDigitList) = args.partition(port => !toInt(port).isEmpty)
      val (portList, tooLargeList) = digitList.partition(port => port.toInt >= 0 && port.toInt < (1 << 16))
      
      if(noDigitList.length > 0 || tooLargeList.length > 0){
        val ports = (noDigitList ++ tooLargeList).mkString(", ")
        println(s"$ports are not port format.(0~65535)")
      }else
        startup(args)
    }
  }

  def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case e: Exception => None
    }
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port when specified as program argument
      val config =
        ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + port).withFallback(
          ConfigFactory.parseString("akka.cluster.roles = [compute]")).withFallback(ConfigFactory.load("akka-cluster"))
          .withFallback(ConfigFactory.load("stats1"))

      val system = ActorSystem("ClusterSystem", config)
      system.actorOf(Props[PdfWorker], name = "pdfWorker")
    }
  }
  
  sealed trait Output
  sealed trait ResultCode

  final case class FilePdf(path: String) extends Output
  final case class MemoryPdf(name: String, bytes: Array[Byte]) extends Output

  final case class HtmlToPdfFileJob(url: String, fullPath: String)
  final case class HtmlToPdfMemoryJob(url: String)
  final case class TransResult(code: ResultCode, output: Option[Output])

  final case class FileMerge2FileJob(pdfList: List[String], fullPath: String)
  final case class FileMerge2MemoryJob(pdfList: List[String])
  final case class MemoryMerge2FileJob(pdfList: List[Tuple2[String, Array[Byte]]], fullPath: String)
  final case class MemoryMerge2MemoryJob(pdfList: List[Tuple2[String, Array[Byte]]])

  final case class MergeResult(code: ResultCode, output: Option[Output])

  final case class Success(msg: String) extends ResultCode
  final case class Fail(msg: String) extends ResultCode
  final case class FileReadError(msg: String) extends ResultCode
  final case class FileWriteError(msg: String) extends ResultCode
  final case class MemoryReadError(msg: String) extends ResultCode
  final case class MemoryWriteError(msg: String) extends ResultCode
  final case class UriError(msg: String) extends ResultCode
  final case class TranslationError(msg: String) extends ResultCode
  
}


class PdfWorker extends Actor {
  val cluster = Cluster(context.system)
  val log = Logging(context.system, this)
  var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    log.info("ready to terminate");
    cluster.unsubscribe(self)
    cluster.leave(cluster.selfAddress)
    context.system.terminate()
  }
  def filePresenceValidate(list: List[String]) = {
    if(list.length == 0)
      false
    else if(!list.forall((s: String) => Files.exists(Paths.get(s))))
      false
    else
      true
  }
  def memoryPresenceValidate(list: List[Tuple2[String, Array[Byte]]]) = {
    if(list.length == 0)
      false
    else
      true
  }
  def uriValidate(uri: String) = {
    try{
      new URL(uri)
      true
    }catch{
      case e: Exception => log.info(e.toString)
        false
    }
  }
  def receive = {
    case "tick" => {
      log.info(sender.toString + ": tick called");
      sender() ! "tok"
    }
    case FileMerge2FileJob(pdfList, fullPath) =>
      if(filePresenceValidate(pdfList)){
        try{
          val is = PDFUtilWrapper.merge(pdfList.map{path =>
            val fis = new FileInputStream(path);
            fis.asInstanceOf[InputStream]
          }.asJava)
          val fo = new FileOutputStream(new File(fullPath))
          readAndWrite(is, fo)
          sender() ! MergeResult(Success("merged"), Some(FilePdf(fullPath)))
        }catch{
          case e: FileNotFoundException => sender() ! MergeResult(FileWriteError(e.toString), None)
          case e: Exception => sender() ! MergeResult(Fail(e.toString), None)
        }
      }else{
        sender() ! MergeResult(FileReadError("invalid pdfList is passed"), None)
      }
      log.info(pdfList.toString)

    case FileMerge2MemoryJob(pdfList) =>
      if(filePresenceValidate(pdfList)){
        val is = PDFUtilWrapper.merge(pdfList.map{path =>
          val fis = new FileInputStream(path);
          fis.asInstanceOf[InputStream]
        }.asJava)
        val fo = new ByteArrayOutputStream()
        readAndWrite(is, fo)
        val result = fo.toByteArray()
        sender() ! MergeResult(Success("merged"), Some(MemoryPdf("", result)))
      }else{
        sender() ! MergeResult(FileReadError("invalid pdfList is passed"), None)
      }
      log.info(pdfList.toString)

    case MemoryMerge2FileJob(pdfList, fullPath) =>
      if(memoryPresenceValidate(pdfList)){
        try{
          val is = PDFUtilWrapper.merge(pdfList.map{pdf =>
            val bis = new ByteArrayInputStream(pdf._2);
            bis.asInstanceOf[InputStream]
          }.asJava)
          val fo = new FileOutputStream(fullPath)
          readAndWrite(is, fo)
          sender() ! MergeResult(Success("merged"), Some(FilePdf(fullPath)))
        }catch{
          case e: FileNotFoundException => sender() ! MergeResult(FileWriteError(e.toString), None)
          case e: Exception => sender() ! MergeResult(Fail(e.toString), None)
        }
      } else {
        sender() ! MergeResult(MemoryReadError("invalid pdfList is passed"), None)
      }
      log.info(pdfList.toString)

    case MemoryMerge2MemoryJob(pdfList) =>
      if(memoryPresenceValidate(pdfList)){
        try{
          val is = PDFUtilWrapper.merge(pdfList.map{pdf =>
            val bis = new ByteArrayInputStream(pdf._2);
            bis.asInstanceOf[InputStream]
          }.asJava)
          val fo = new ByteArrayOutputStream()
          readAndWrite(is, fo)
          val result = removeEmptyPages(fo.toByteArray())

          sender() ! MergeResult(Success("merged"), Some(MemoryPdf("", result)))
        }catch{
          case e: Exception => sender() ! MergeResult(Fail(e.toString), None)
        }
      } else {
        sender() ! MergeResult(MemoryReadError("invalid pdfList is passed"), None)
      }
      log.info(pdfList.toString)

    case HtmlToPdfFileJob(uri, fullPath) =>
      if(uriValidate(uri)){
        htmlToPdf(uri, fullPath) match {
          case Some(process) =>
            sender() ! TransResult(Success("trans"), Some(FilePdf(fullPath)))
          case None =>
            sender() ! TransResult(TranslationError("trans fail"), None)
        }
      }else{
        sender() ! TransResult(UriError("invalid uri error"), None)
      }
      log.info(uri.toString)
      
    case HtmlToPdfMemoryJob(uri) =>
      try{
        if(uriValidate(uri)){
          val tempFile = File.createTempFile("pdfakka", System.currentTimeMillis.toString + ".pdf")
          tempFile.delete()
          log.info("source absolute uri: " + uri)
          log.info("dest absolute path: " + tempFile.getAbsolutePath)
          htmlToPdf(uri, tempFile.getAbsolutePath) match {
            case Some(process) =>
              val fis = new FileInputStream(tempFile)
              val buffer = new mutable.ArrayBuffer[Byte]
              Stream.continually(fis.read()).takeWhile(_ != -1).map(_.toByte).foreach(buffer += _)
              sender() ! TransResult(Success("trans"), Some(MemoryPdf("", buffer.toArray)))
              tempFile.delete()
            case None =>
              sender() ! TransResult(TranslationError("trans fail"), None)
          }
        }else{
          sender() ! TransResult(UriError("invalid uri error"), None)
        }
      }catch{
        case e: IOException =>
          sender() ! TransResult(Fail(e.toString), None)
      }
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up => m.address
      }
    case MemberUp(m) if m.hasRole("compute") =>
      log.info("MemberUp: " + m.toString)
      
    case UnreachableMember(m) =>
      log.info("UnreachableMember: " + m.toString)

    case ReachableMember(m) if m.hasRole("compute") =>
      log.info("ReachableMember: " + m.toString)
      
  }
}



