package com.briskware.test.functional

import akka.actor._
import akka.util.Timeout
import com.briskware.test.functional.FusekiManager._
import spray.client.pipelining._
import spray.http.{StatusCodes, HttpResponse}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object FusekiManager {

  trait Message
  case object Start extends Message
  case object StartOk extends Message
  case object StartError extends Message
  case object Shutdown extends Message
  case object ShutdownOk extends Message
  case object ShutdownError extends Message

  private case class Ping(respondTo: ActorRef, sendOnSuccess: Option[Message] = None, sendOnFailure: Option[Message] = None, waitingTime: Duration = 500 milliseconds, retries: Int = 20)
  private case class SoftShutdownRequested(respondTo: ActorRef)
  private case class HardShutdownRequested(respondTo: ActorRef)

  private class FusekiRunner(val port: Int) extends Thread {

    var process: Option[Process] = None

    override def run(): Unit = {
      val path = new org.apache.jena.fuseki.Fuseki().getClass.getProtectionDomain.getCodeSource.getLocation.getPath
      val cmd = s"java -jar $path --port=$port --mem --update /test"
      println(s"Launching Fuseki Server: $cmd")
      process = Some(Runtime.getRuntime.exec(cmd))
    }

    def startServer(): Unit = {
      start()
    }

    def shutdownServer(): Unit = {
      process.map(_.destroy())
      process = None

      /* this looks scary but the line above should kill this thread immediately so joining should not take too much time */
      this.join()
    }

  }

}

class FusekiManager(val port: Int) extends Actor with ActorLogging {

  import context.dispatcher
  implicit val timeout = Timeout(5 seconds)

  private val fusekiRunner = new FusekiRunner(port)

  private def fusekiEndpoint(path: String): String = s"http://localhost:${port}/${path}"

  val shutdownReq = Post(fusekiEndpoint("$/server/shutdown"))

  val pingReq = Get(fusekiEndpoint("$/ping"))

  val pipeline = sendReceive

  override def receive: Receive = {

    case Start =>
      /* kick off the server */
      Future {
        fusekiRunner.startServer()
      }
      /* start pinging the server and issue StartOk to the sender when ping is successful */
      self ! Ping(sender, Some(StartOk), None)

    case x @ Ping(originalSender, sendOnSuccess, sendOnFailure, duration, retries) => pipeline(pingReq) onComplete {
      case Success(HttpResponse(StatusCodes.OK, _, _, _)) =>
        log.info(s"ping response received for $x")
        sendOnSuccess foreach { originalSender ! _ }
        if ( sendOnSuccess.isEmpty ) {
          log.info(s"re-sending ping on success")
          context.system.scheduler.scheduleOnce(1 second, self, Ping(originalSender, sendOnSuccess, sendOnFailure, duration, retries-1))
        }
      case _ =>
        log.info(s"ping failed for $x")
        sendOnFailure foreach { originalSender ! _ }
        if ( sendOnFailure.isEmpty ) {
          log.info(s"re-sending ping on failure")
          context.system.scheduler.scheduleOnce(1 second, self, Ping(originalSender, sendOnSuccess, sendOnFailure, duration, retries-1))
        }
    }

    case Shutdown =>
      val originalSender = sender
      pipeline(shutdownReq) onComplete {
      case Success(HttpResponse(StatusCodes.OK, _, _, _)) =>
        self ! SoftShutdownRequested(originalSender)
      case x @ _ =>
        /* The Fuseki actually has not implemented the shutdown call yet,
         * so we just do a hard shutdown, which effectively means killing the spawned process.
         */
        log.warning(s"soft shutdown failed with: $x, will attempt a hard kill")
        self ! HardShutdownRequested(originalSender)
    }

    case SoftShutdownRequested(originalSender) =>
      // soft shutdown succeeds if pings start failing eventually
      /* start pinging the server and issue ShutdownOk to the sender when ping is no longer successful */
      self ! Ping(originalSender, None, Some(ShutdownOk))

    case HardShutdownRequested(originalSender) =>
      Future {
        fusekiRunner.shutdownServer()
      } onComplete {
        case Success(_) =>
          originalSender ! ShutdownOk
        case Failure(_) =>
          originalSender ! ShutdownError
      }

  }

}
