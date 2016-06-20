package com.modelfabric.test

import akka.actor._
import akka.util.Timeout
import com.modelfabric.sparql.util.HttpEndpoint
import com.modelfabric.test.FusekiManager._
import spray.client.pipelining._
import spray.http.{HttpResponse, StatusCodes}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object FusekiManager {

  sealed trait Message
  case object Start extends Message
  case object StartOk extends Message
  case object StartError extends Message
  case object Shutdown extends Message
  case object ShutdownOk extends Message
  case object ShutdownError extends Message

  private case class Ping(
    respondTo: ActorRef,
    sendOnSuccess: Message,
    sendOnFailure: Message,
    pingInterval: Duration = 1 second,
    stopOnSuccess: Boolean = true,
    retriesLeft: Int = 10)
  private case class SoftShutdownRequested(respondTo: ActorRef)
  private case class HardShutdownRequested(respondTo: ActorRef)

  private class FusekiRunner(val port: Int, val resource: String) extends Thread {

    var process: Option[Process] = None

    override def run(): Unit = {
      val path = new org.apache.jena.fuseki.Fuseki().getClass.getProtectionDomain.getCodeSource.getLocation.getPath
      val cmd = s"java -jar $path --port=$port --mem --update $resource"
      println(s"Launching Fuseki Server: $cmd")
      process = Some(Runtime.getRuntime.exec(cmd))
    }

    def startServer(): Unit = {
      start()
    }

    def shutdownServer(): Unit = {
      process.foreach(_.destroy())
      process = None
    }
  }
}


/**
  * Class responsible for managing the runtime of a Fuseki server, which is launched as a separate process.
  *
  * The Actor will manage the server's startup and shutdown and will continuously ping the server
  * until it is up and running. Depending on the machine, the server might take between 2 and 10 seconds to start.
  *
  * Pinging the server every 500ms will ensure that the tests can start the moment the server is up (i.e. it responds
  * to a Ping request). The server startup and shutdown is managed via [[HttpEndpointSuiteTestRunner]] Suite's
  * [[org.scalatest.BeforeAndAfter]] hooks.
  *
  * @param endpoint the HttpEndpoint instance to bind too, e.g. "localhost"
  */
class FusekiManager(val endpoint: HttpEndpoint) extends Actor with ActorLogging {

  import context.dispatcher
  implicit val timeout = Timeout(5 seconds)

  private val fusekiRunner = new FusekiRunner(endpoint.port, endpoint.resource)

  private def fusekiEndpoint(path: String): String = s"http://${endpoint.host}:${endpoint.port}/${path}"

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
      self ! Ping(sender, sendOnSuccess = StartOk, sendOnFailure = StartError)

    case x @ Ping(originalSender, sendOnSuccess, sendOnFailure, pingInterval, stopOnSuccess, 0) =>
      log.info(s"Ping timeout for $x")
      originalSender ! sendOnFailure
      // kill the managed server as we timeout out on the pings
      self ! HardShutdownRequested(originalSender)

    case x @ Ping(originalSender, sendOnSuccess, sendOnFailure, pingInterval, stopOnSuccess, retriesLeft) =>
      val stopOnFailure = !stopOnSuccess
      log.info(s"Sending: $pingReq")
      pipeline(pingReq) onComplete {
        case Success(HttpResponse(StatusCodes.OK, _, _, _)) =>
          log.info(s"ping response received for $x")
          if ( stopOnSuccess ) {
            originalSender ! sendOnSuccess
          } else {
            log.info(s"re-sending ping on success")
            context.system.scheduler.scheduleOnce(1 second, self, x.copy(retriesLeft = retriesLeft-1))
          }
        case _ =>
          log.info(s"ping failed for $x")
          if ( stopOnFailure ) {
            originalSender ! sendOnFailure
          } else {
            log.info(s"re-sending ping on failure")
            context.system.scheduler.scheduleOnce(1 second, self, x.copy(retriesLeft = retriesLeft-1))
          }
      }

    case Shutdown =>
      val originalSender = sender
      log.info(s"Sending: $shutdownReq")
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
      /* soft shutdown succeeds if pings start failing eventually */
      /* start pinging the server and issue ShutdownOk to the sender when ping is no longer successful */
      self ! Ping(originalSender, sendOnSuccess = ShutdownError, sendOnFailure = ShutdownOk, stopOnSuccess = false)

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
