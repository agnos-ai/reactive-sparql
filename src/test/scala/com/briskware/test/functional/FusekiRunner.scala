package com.briskware.test.functional

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.util.Timeout
import spray.client.pipelining._
import spray.http.{StatusCodes, HttpRequest, HttpResponse}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Try}

object FusekiRunner {
  private def fusekiEndpoint(path: String): String = s"http://localhost:3030/${path}"

  def apply(implicit system: ActorSystem) = {
    new FusekiRunner()
  }
}

class FusekiRunner(implicit val system: ActorSystem) extends Thread {

  import FusekiRunner._

  import system.dispatcher
  implicit val timeout = Timeout(5 seconds)

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  val shutdownReq = Post(fusekiEndpoint("$/server/shutdown"))

  val pingReq = Get(fusekiEndpoint("$/ping"))

  override def run(): Unit = {
    val args = Array("--mem", "--update", "/test")
    org.apache.jena.fuseki.cmd.FusekiCmd.main(args:_*)
  }

  def startServer(): Unit = {
    start()

    // TODO: this is bad, we should continuously ping the server until it becomes available
    Thread.sleep(10000)
    println(s"XXXX ====> ${waitForSuccess(pipeline(pingReq))}")
  }

  def shutdownServer(): Unit = {
    // This is not supported yet: https://jena.apache.org/documentation/fuseki2/fuseki-server-protocol.html
    println(s"XXXX ====> ${waitForSuccess(pipeline(shutdownReq))}")

    // Killing the thread
    println(s"Killing the thread: ${Try(this.interrupt())}")
  }

  def waitForSuccess(request: => Future[HttpResponse]): Boolean = {

    request onFailure {
      case e @ _ =>
        println(s"ERR: $e")
        waitForSuccess(request)
    }

    val res = request map {
      case HttpResponse(StatusCodes.OK,_, _, _) => true
      case HttpResponse(StatusCodes.NotFound, _, _, _) => false
      case _ =>
        println(".")
        waitForSuccess(request)
    }

    Await.result(res, timeout.duration)
  }

}

object FusekiManagerActor {
  case object Startup
  case object Shutdown
  case object WaitingForResponse
  case class ResponseReceived(status: Boolean)
}
class FusekiManagerActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case _ => // TODO
  }

}