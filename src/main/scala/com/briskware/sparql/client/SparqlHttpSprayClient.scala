package com.briskware.sparql.client

import _root_.spray.can.parsing.ParserSettings
import akka.actor._
import com.briskware.sparql.{SparqlQuery, SparqlStatement, SparqlUpdate}
import com.briskware.sparql.SparqlStatement
import com.modelfabric.extension.StringExtensions._

import scala.language.postfixOps

import javax.ws.rs.core.UriBuilder

import spray.http.HttpCharsets._
import spray.client.pipelining._
import spray.http._
import spray.json.JsonParser
import scala.concurrent.{ Await, Future }
import HttpMethods._
import SparqlHttpSprayClient._
import scala.io.Source
import spray.http.HttpHeaders.RawHeader
import scala.util.Failure
import spray.http.HttpResponse
import scala.util.Success
import spray.http.HttpRequest
import spray.http.Uri
import spray.can.client.{ ClientConnectionSettings, HostConnectorSettings }
import spray.client.pipelining
import spray.can.Http
import spray.can.Http.{ HostConnectorInfo, HostConnectorSetup }
import java.net.InetSocketAddress
import akka.io
import akka.pattern._
import akka._
import akka.util.Timeout
import scala.concurrent.duration.Duration

object UrlPart {

  def unapply(in: java.net.URL) = decode(in)
  def unapply(in: Uri) = decode(new java.net.URL(in.toString()))

  private def decode(in: java.net.URL) = Some((
    in.getProtocol,
    in.getHost,
    in.getPort,
    in.getPath
  ))
}

object SparqlHttpSprayClient {

  //
  // Constants taken from http://grepcode.com/file/repo1.maven.org/maven2/
  // org.openrdf.sesame/sesame-http-protocol/2.7.0-beta1/org/openrdf/http/protocol/Protocol.java
  //
  val FORM_MIME_TYPE = "application/x-www-form-urlencoded"
  val SPARQL_RESULTS_MIME_TYPE = "application/sparql-results+json"
  val BASEURI_PARAM_NAME = "baseURI"
  val QUERY_LANGUAGE_PARAM_NAME = "queryLn"
  val QUERY_PARAM_NAME = "query"
  val UPDATE_PARAM_NAME = "update"
  val GRAPH_PARAM_NAME = "graph"
  val TIMEOUT_PARAM_NAME = "timeout"
  val INSERT_GRAPH_PARAM_NAME = "insert-graph-uri"
  val USING_GRAPH_PARAM_NAME = "using-graph-uri"
  val USING_NAMED_GRAPH_PARAM_NAME = "using-named-graph-uri"
  val DEFAULT_GRAPH_PARAM_NAME = "default-graph-uri"
  val NAMED_GRAPH_PARAM_NAME = "named-graph-uri"
  val ACCEPT_PARAM_NAME = "Accept"
  val STATEMENTS = "statements"
  val INCLUDE_INFERRED_PARAM_NAME = "infer"

  val `application/sparql-results+json` = MediaType.custom(SPARQL_RESULTS_MIME_TYPE)
  MediaTypes.register(`application/sparql-results+json`)
  MediaTypes.register(MediaType.custom(FORM_MIME_TYPE))

  // RFC4627 defines JSON to always be UTF encoded, we always render JSON to UTF-8
  val `application/x-www-form-urlencoded` = ContentType(MediaTypes.`application/x-www-form-urlencoded`, `UTF-8`)

  def createActor(
    factory : ActorRefFactory,
    config : SparqlClientConfig,
    requestor : ActorRef,
    statement : SparqlStatement,
    retry : Int) = {
    val props = statement match {
      case statement : SparqlQuery ⇒
        Props(classOf[ExtendedSparqlQuery], requestor, statement, config)
      case statement : SparqlUpdate ⇒
        Props(classOf[ExtendedSparqlUpdate], requestor, statement, config)
    }
    factory.actorOf(props) ! MessageConnect(retry)
  }
}

/**
 * A simple spray-client based SPARQL client
 */
class SparqlHttpSprayClient(
    config : SparqlClientConfig) extends SparqlClient(config) {

  override def execute(requestor_ : ActorRef, statement_ : SparqlStatement) {
    createActor(context, config, requestor_, statement_, 0)
  }
}

private case class MessageExecute(connection : ActorRef, attempt : Int)
private case class MessageConnect(attempt : Int)

/**
 * Wrapper around SparqlStatement, abstract base class for ExtendedSparqlQuery and ExtendedSparqlUpdate
 */
private sealed abstract class ExtendedSparqlStatement(
    protected val requestor : ActorRef,
    protected val statement : SparqlStatement,
    protected val config : SparqlClientConfig) extends Actor with ActorLogging {

  import context.dispatcher

  require(config.endPoint.isDefined, "Endpoint not defined")

  implicit val timeout = statement.executionTimeout
  implicit val actorSystem = context.system
  implicit val actorSystemTimeout = Timeout(timeout)

  val endPoint = UriBuilder.fromPath("{arg1}").build(config.endPoint.get)
  val endPointURI = Uri.parseAbsolute(config.endPoint.get)
  val actualSparql = statement.statement

  val uri : Uri

  /**
   *  Optimizing pipeline creation by this partially applied function that
   *  defines a pipeline before it takes the connection actor.
   */
  lazy val pipeline = defPipeline _
  def defPipeline(connection : ActorRef) : HttpRequest ⇒ Future[HttpResponse]

  val postBody : HttpEntity

  def httpRequest : HttpRequest = statement.httpMethod match {
    case GET    ⇒ Get(uri)
    case POST   ⇒ Post(uri, postBody)
    case method ⇒ throw new IllegalArgumentException(s"Unsupported HTTP Method $method")
  }

  def hostHeader = addHeader(HttpHeaders.Host(uri.authority.host.address, uri.authority.port))
  def credentials = addCredentials(BasicHttpCredentials(config.userId.get, config.password.get))
  def acceptHeader = addHeader(RawHeader(ACCEPT_PARAM_NAME, SPARQL_RESULTS_MIME_TYPE))
  def acceptAllHeader = addHeader(RawHeader(ACCEPT_PARAM_NAME, "*/*"))
  def formHeader = addHeader(RawHeader("Content-Type", s"${FORM_MIME_TYPE}; charset=utf-8"))

  /**
   * Patches the default host connection settings
   * with additional per-query specific properties
   */
  lazy val setup = {

    val clientSettings = ClientConnectionSettings(context.system).copy (
      idleTimeout = statement.idleTimeout,
      requestTimeout = statement.receiveTimeout
    )

    val settings = HostConnectorSettings(context.system).copy(
      pipelining = false, // turn pipelining off as some sparql endpoints are struggling
      idleTimeout = statement.idleTimeout,
      connectionSettings = clientSettings
    )

    endPointURI match {
      case UrlPart(_, host, port, _) ⇒
        HostConnectorSetup(host = host, port = port, settings = Some(settings))
    }
  }

  /**
   * Returns a lazy future which will retrieve a configured connection
   * actor based on the setup it gets passed.
   */
  lazy val connect = {
    io.IO(Http).ask(setup).map {
      case HostConnectorInfo(hostConnector, _) ⇒ hostConnector
    }
  }

  def responseFuture(connection : ActorRef) : Future[HttpResponse] = {
    try {
      pipeline(connection)(httpRequest)
    }
    catch {
      case e : Throwable ⇒ log.error(e, "caught exception while processing future"); throw e
    }
  }

  def handleResponse(response_ : HttpResponse, attempt : Int)

  /**
   * Log the error and pretty print the corresponding SPARQL statement, then send the MessageSparqlStatementFailed
   * message to the requestor.
   *
   * In case the retryCount has not been reached, send a connect message again and re-attempt the operation.
   * The Sparql statement will not be logged until the retryCount has been reached. Instead a warning with the exception
   * is logged every time an attempt is made.
   *
   * @return true if the operation is retried, false if not
   */
  def handleError(response_ : Option[HttpResponse], error_ : Option[Throwable], retry_ : Int) : Boolean = {

    if (retry_ < config.retryCount) {
      log.warning(s"Sparql Statement WILL BE RETRIED (attempt=${retry_ + 1} of retry count=${config.retryCount + 1} has not been reached) Response: ${response_}  Error: ${error_}")
      // create new a new ROOT actor for the next attempt as the old one has been expired by spray
      createActor(context.system, config, requestor, statement, retry_ + 1)
      // return true signaling retry is pending, however
      // don't proceed - we do not need to report the statement on retry
      return true
    }

    if (config.retryCount > 0)
      log.warning(s"Retry count has been reached, Sparql Statement has been executed ${config.retryCount + 1} times and still fails, will raise an error")

    /*
     * Add line numbers
     */
    def lineNumberedSPARQL(sparql : String) = {
      val sb = StringBuilder.newBuilder
      val lines = Source.fromString(sparql).getLines()

      var n = 1; while (lines.hasNext) {
        val line = lines.next()
        if (!line.isEmpty) {
          if (n > 1) {
            sb ++= f"$n%03d> $line%s\n"
          }
          else {
            sb ++= f"$line%s\n"
          }
          n += 1
        }
      }
      sb.toString()
    }

    val builder = StringBuilder.newBuilder.append("SPARQL Statement resulted in error ")
    if (response_.isDefined) builder
      .append(response_.get.status.defaultMessage).append('\n')
      .append(response_.get.entity.asString).append('\n')
    if (error_.isDefined) builder
      .append(error_.get).append('\n')
    builder.append(lineNumberedSPARQL(actualSparql)).append('\n')

    log.error(builder.toString)

    requestor ! MessageSparqlStatementFailed(statement, response_, error_)

    return false
  }

  def receive : Receive = {
    case MessageConnect(attempt) ⇒ connect onComplete {
      case Success(connection) ⇒
        log.debug("Connection configured: {}", connection)
        self ! MessageExecute(connection, attempt)
      case Failure(error) ⇒
        log.error(error, "Failure while configuring client connection")
        handleError(None, Option(error), attempt)
        // kill self as no-one else will otherwise
        self ! PoisonPill
    }
    case MessageExecute(connection, attempt) ⇒ responseFuture(connection) onComplete {
      case Success(response) ⇒
        handleResponse(response, attempt)
        // kill self as no-one else will otherwise
        self ! PoisonPill

      case Failure(error) ⇒
        log.error(error, "Failure while executing request")
        handleError(None, Option(error), attempt)
        // kill self as no-one else will otherwise
        self ! PoisonPill
    }
  }
}

/**
 * Wrapper around SparqlQuery, handles the specifics of dealing with SPARQL SELECT statements.
 * It sends a MessageSparqlClientQuerySolution-message to the requestor for each found QuerySolution and a
 * MessageSparqlClientQueryEnd-message at the end.
 */
private class ExtendedSparqlQuery(
    requestor_ : ActorRef,
    statement_ : SparqlQuery,
    config_ : SparqlClientConfig) extends ExtendedSparqlStatement(requestor_, statement_, config_) {

  lazy val bodyContent = s"$QUERY_PARAM_NAME=${actualSparql.urlEncode}"
  lazy val postBody = HttpEntity(`application/x-www-form-urlencoded`, bodyContent)

  lazy val uri : Uri = statement.httpMethod match {
    case POST ⇒ endPointURI
    case _ ⇒ Uri(
      endPointURI.scheme,
      endPointURI.authority,
      endPointURI.path,
      (QUERY_PARAM_NAME, actualSparql) +: Uri.Query.Empty,
      None
    )
  }

  val postContentTypeHeader = statement.httpMethod match {
    case POST ⇒ formHeader
    case _    ⇒ (request : HttpRequest) ⇒ { request }
  }

  /**
   * Create and return the HTTP pipeline.
   */
  def defPipeline(connection : ActorRef) : HttpRequest ⇒ Future[HttpResponse] = {

    import concurrent.ExecutionContext.Implicits.global

    val p0 = hostHeader
    val p1 = p0 ~> credentials
    val p2 = p1 ~> acceptHeader
    val p3 = p2 ~> postContentTypeHeader
    val p4 = p3 ~> sendReceive(connection)

    p4
  }

  override def handleResponse(response_ : HttpResponse, attempt : Int) {

    import SparqlClientJsonProtocol._

    if (!response_.status.isSuccess) {
      handleError(Option(response_), None, attempt)
      return
    }

    if (attempt > 0) {
      log.info(s"Sparql Query RETRY operation (attempt=${attempt + 1}) SUCCESSFUL")
    }

    val json = JsonParser(response_.entity.asString)
    val resultSet = json.convertTo[ResultSet]

    if (resultSet.results.bindings.size == 0) {
      requestor ! MessageSparqlClientNoQuerySolutions(statement_)
    }
    else {
      resultSet.results.bindings foreach {
        qs ⇒ requestor ! MessageSparqlClientQuerySolution(statement_, qs)
      }
    }

    requestor ! MessageSparqlClientQueryEnd(statement_, resultSet)
  }
}

/**
 * Wrapper around SparqlUpdate, handles the specifics of dealing with SPARQL UPDATE statements.
 */
private class ExtendedSparqlUpdate(
    requestor_ : ActorRef,
    statement_ : SparqlUpdate,
    config_ : SparqlClientConfig) extends ExtendedSparqlStatement(requestor_, statement_, config_) {

  import context.dispatcher

  require(statement.httpMethod == POST)

  lazy val postBody = HttpEntity(`application/x-www-form-urlencoded`, queryParams.mkString("&"))

  lazy val queryParams = List(
    s"$QUERY_LANGUAGE_PARAM_NAME=SPARQL",
    s"$INCLUDE_INFERRED_PARAM_NAME=true",
    s"$UPDATE_PARAM_NAME=${actualSparql.urlEncode}"
  )

  lazy val uri : Uri = endPointURI.copy(path = endPointURI.path / STATEMENTS)

  /**
   * Create and return the HTTP pipeline.
   */
  def defPipeline(connection : ActorRef) = {

    hostHeader ~> formHeader ~> credentials ~> sendReceive(connection)
  }

  override def handleResponse(response_ : HttpResponse, attempt : Int) {

    if (response_.status != StatusCodes.NoContent) { // 204 is actually ok in this case
      handleError(Option(response_), Some(new RuntimeException(s"${response_.status.value} (${response_.status.reason})")), attempt)
      return
    }

    if (attempt > 0) {
      log.info(s"Sparql Update RETRY operation (attempt=${attempt + 1}) SUCCESSFUL")
    }

    log.info("Successfully executed {}", statement.statement)

    requestor_ ! MessageSparqlClientUpdateSuccessful(statement_)
  }
}

