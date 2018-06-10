package org.modelfabric.sparql.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.model.HttpMethods._


trait SparqlClientProtocol extends ClientAPIProtocol

/**
  * Represents a single Sparql request to be sent to the triple store.
  *
  * @param statement the sparql statement string, any | margins will be stripped automatically
  * @param context an optional context to be carried along the flow
  */
case class SparqlRequest(statement: SparqlStatement, context: Option[Any] = None) extends ClientHttpRequest with SparqlClientProtocol {
  override def httpMethod = GET
}

/**
  * "Is-Alive" request
  */
// JC: not used
case object PingRequest extends Request

/**
  * Represents a response from the triple store for a Sparql request.
  *
  * @param request the underlying request object is returned with the response
  * @param success true if the sparql statement execution succeeded
  * @param status the HTTP status of the result
  * @param result results
  * @param error optional error message, if available
  */
case class SparqlResponse(
  request: SparqlRequest,
  success: Boolean = true,
  status: StatusCode = StatusCodes.OK,
  result: List[SparqlResult] = Nil,
  error: Option[SparqlClientError] = None) extends ClientHttpResponse with SparqlClientProtocol

/**
  * "Is-Alive" response
  *
  * @param success
  */
// JC: not used
case class PingResponse(success: Boolean = true) extends ClientHttpResponse with SparqlClientProtocol


/**
  * Error messages
  */
sealed trait SparqlClientError
case class SparqlClientRequestFailed(message: String) extends RuntimeException(message) with SparqlClientError
case class SparqlClientRequestFailedWithError(message: String, throwable: Throwable) extends RuntimeException(message, throwable) with SparqlClientError

trait ErrorHandlerSupport {
  def errorHandler: ErrorHandler
}

abstract class ErrorHandler {
  def handleError(error: Throwable)(implicit system: ActorSystem): Unit
}

object DefaultErrorHandler extends ErrorHandler {
  override def handleError(error: Throwable)(implicit system: ActorSystem): Unit = {
    system.log.error(s"STREAM FAILED with: $error")
    throw new RuntimeException("unexpected error encountered, collapsing stream", error)
  }
}

object IgnoreAndLogErrorHandler extends ErrorHandler {
  override def handleError(error: Throwable)(implicit system: ActorSystem): Unit = {
    system.log.error(s"IGNORED: SPARQL client encountered an error shown below an will continue")
    error.printStackTrace(System.err)
  }
}

case class SystemExitErrorHandler(exitCode: Int) extends ErrorHandler {
  override def handleError(error: Throwable)(implicit system: ActorSystem): Unit = {
    system.log.error(s"CRITICAL: Error attached below, the JVM will exit with code $exitCode")
    error.printStackTrace(System.err)
    System.exit(exitCode)
  }
}
