package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials, Authorization}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Graph, FlowShape}
import akka.stream.scaladsl._
import com.modelfabric.extension.StringExtensions._
import com.modelfabric.sparql.api.HttpMethod
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.mapper.SparqlClientJsonProtocol._

import com.modelfabric.sparql.util.{BasicAuthentication, HttpEndpoint}

import scala.concurrent.{ExecutionContext, Future}

object Builder {

  /**
    * Create a partial flow Graph of sparql requests to results.
    * {{{
    *                +----------------------------------------------+
    *                | Load-balanced Disordered Sparql Request Flow |
    *                |                                              |
    *                |  +-----+        +----------+        +-----+  |
    *                |  |     |        |          |        |     |  |
    *                |  |     | ~Out~> | Request1 | ~Out~> |     |  |
    *                |  |     |        |          |        |     |  |
    *                |  |     |        +----------+        |     |  |
    *                |  |     |                            |     |  |
    *                |  |     |        +----------+        |     |  |
    *                |  |     |        |          |        |     |  |
    *                |  |  B  | ~Out~> | Request2 | ~Out~> |  M  |  |
    *                |  |  a  |        |          |        |  e  |  |
    *                |  |  l  |        +----------+        |  r  |  |
    * SparqlRequest ~~> |  a  |                            |  g  | ~~> SparqlResponse
    *                |  |  n  |             .              |  e  |  |
    *                |  |  c  |             .              |     |  |
    *                |  |  e  |             .              |     |  |
    *                |  |     |                            |     |  |
    *                |  |     |        +----------+        |     |  |
    *                |  |     |        |          |        |     |  |
    *                |  |     | ~Out~> | RequestN | ~Out~> |     |  |
    *                |  |     |        |          |        |     |  |
    *                |  +-----+        +----------+        +-----+  |
    *                |                                              |
    *                +-----------------------------------------------+
    * }}}
    *
    * Use this flow if you have a larger set of requests to run and you don't necessarily
    * care in what order do the query responses arrive.
    * JC: as we discussed, there is probably no such case.
    *
    * Note: this builder uses the connection-level API, so every parallel sub-stream will
    * materialize and use it's own connection.
    *
    * @param endpoint the HTTP endpoint of the Sparql triple store server
    * @param parallelism the number of concurrent streams to use
    * @param _system the implicit actor system
    * @param _materializer the actor materializer
    * @param _context the Futures execution context
    * @return
    */
  def loadBalancedDisorderedSparqlRequestFlow(
    endpoint: HttpEndpoint,
    parallelism: Int
  )(implicit
      _system: ActorSystem,
      _materializer: ActorMaterializer,
      _context: ExecutionContext
  ) : Graph[FlowShape[SparqlRequest, SparqlResponse], NotUsed] = {

    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val dispatcher = builder.add(Balance[SparqlRequest](parallelism))
      val merger = builder.add(Merge[SparqlResponse](parallelism))
      for ( i <- 0 until parallelism) {
        dispatcher.out(i) ~> sparqlRequestFlow(endpoint) ~> merger.in(i)
      }

      FlowShape(dispatcher.in, merger.out)

    } named "flow.loadBalancedDisorderedSparqlRequestFlow"

  }

  /**
    * Create a partial flow Graph of sparql requests to results.
    * {{{
    *
    * TODO
    *
    * }}}
    *
    * @param endpoint the HTTP endpoint of the Sparql triple store server
    * @param _system the implicit actor system
    * @param _materializer the actor materializer
    * @param _context the Futures execution context
    * @return
    */
  def sparqlRequestFlow(
    endpoint: HttpEndpoint
  )(implicit
      _system: ActorSystem,
      _materializer: ActorMaterializer,
      _context: ExecutionContext
  ) : Graph[FlowShape[SparqlRequest, SparqlResponse], NotUsed] = {

    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcastRequest = builder.add(Broadcast[SparqlRequest](2))

      val queryFilter = builder.add {
        Flow[SparqlRequest].filter {
          case r@SparqlRequest(SparqlQuery(_,_)) =>
            println(s"Handling QUERY: $r")
            true
          case _ => false
        }
      }

      val updateFilter = builder.add {
        Flow[SparqlRequest].filter {
          case r@SparqlRequest(SparqlUpdate(_,_)) =>
            println(s"Handling UPDATE: $r")
            true
          case _ => false
        }
      }

      val responseMerger = builder.add(Merge[SparqlResponse](2).named("merge.sparqlResponse"))

      // JC: are the broadcast and filter necessary? or it can be just a if else logic when building the flow?
      broadcastRequest ~> queryFilter  ~> sparqlQueryFlow(endpoint)  ~> responseMerger
      broadcastRequest ~> updateFilter ~> sparqlUpdateFlow(endpoint) ~> responseMerger

      FlowShape(broadcastRequest.in, responseMerger.out)

    } named "flow.sparqlRequestFlow"

  }

  /**
    * Create a partial flow Graph of sparql requests to results.
    * {{{
    *
    * TODO
    *
    * }}}
    *
    * @param endpoint the HTTP endpoint of the Sparql triple store server
    * @param _system the implicit actor system
    * @param _materializer the actor materializer
    * @param _context the Futures execution context
    * @return
    */
  def sparqlQueryFlow(
    endpoint: HttpEndpoint
  )(implicit
    _system: ActorSystem,
    _materializer: ActorMaterializer,
    _context: ExecutionContext
  ): Graph[FlowShape[SparqlRequest, SparqlResponse], NotUsed] = {

    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._
      import endpoint._

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpoint)).async.named("mapping.sparqlToHttpRequest"))

      // JC: this will get a new connection, better to use Host-Level api or Request-Level api (if we'll connect to triple store multiple servers),
      // they both use connection pool. http://doc.akka.io/docs/akka/current/scala/http/client-side/index.html
      val queryConnectionFlow = builder.add(Http().outgoingConnection(host, port).async.named("http.sparqlQueryConnection"))

      val broadcastQueryHttpResponse = builder.add(Broadcast[HttpResponse](2).async.named("broadcast.queryResponse"))

      val resultSetParser = builder.add(Flow[HttpResponse].mapAsync(1)(res => responseToResultSet(res)).async.named("mapping.parseResultSet"))

      val resultMaker = builder.add(Flow.fromFunction(responseToSparqlResponse).async.named("mapping.makeResponseFromHeader"))

      val queryResultZipper = builder.add(ZipWith[ResultSet, SparqlResponse, SparqlResponse](
        (resultSet, response) =>
          response.copy(resultSet = Some(resultSet))
      ).async.named("zipper.queryResultZipper"))

      converter ~> queryConnectionFlow ~> broadcastQueryHttpResponse ~> resultSetParser ~> queryResultZipper.in0
                                          broadcastQueryHttpResponse ~> resultMaker     ~> queryResultZipper.in1

      FlowShape(converter.in, queryResultZipper.out)
    } named "flow.sparqlQueryRequest"

  }

  def sparqlUpdateFlow(
    endpoint: HttpEndpoint
  )(implicit
      _system: ActorSystem,
      _materializer: ActorMaterializer,
      _context: ExecutionContext
  ) : Graph[FlowShape[SparqlRequest, SparqlResponse], NotUsed] = {

    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      import endpoint._

      // JC: why all stages in sparqlQueryFlow are async, but not here? do we need to make all of them async?
      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpoint)).named("mapping.sparqlToHttpRequest"))

      val updateConnectionFlow = builder.add(Http().outgoingConnection(host, port).named("http.sparqlUpdate"))

      val broadcastUpdateHttpResponse = builder.add(Broadcast[HttpResponse](2).named("broadcast.updateResponse"))
      val booleanParser = builder.add(Flow[HttpResponse].mapAsync(1)(res => responseToBoolean(res)).named("mapping.parseBoolean"))
      val resultMaker = builder.add(Flow.fromFunction(responseToSparqlResponse).named("mapping.makeResponseFromHeader"))
      val updateResultZipper = builder.add(ZipWith[Boolean, SparqlResponse, SparqlResponse]( (success, response) =>
        response.copy(
          success = success
        )
      ).named("zipper.updateResultZipper"))

      converter ~> updateConnectionFlow ~> broadcastUpdateHttpResponse ~> booleanParser ~> updateResultZipper.in0
                                           broadcastUpdateHttpResponse ~> resultMaker   ~> updateResultZipper.in1

      FlowShape(converter.in, updateResultZipper.out)
    } named "flow.sparqlUpdateRequest"

  }

  private def sparqlToRequest(endpoint: HttpEndpoint)(request: SparqlRequest): HttpRequest = {
    makeHttpRequest(endpoint, request.statement)
  }

  // JC: not good OO design. I think it's better to create a new class SparqlEndpoint
  private def makeHttpRequest(endpoint: HttpEndpoint, sparql: SparqlStatement): HttpRequest = sparql match {
    case SparqlQuery(HttpMethod.GET, query) =>
      HttpRequest(
        method = HttpMethods.GET,
        uri = endpoint.path + s"$QUERY_URI_PART?$QUERY_PARAM_NAME=${sparql.statement.urlEncode}",
        Accept(`application/sparql-results+json`) :: makeRequestHeaders(endpoint)
      )

    case SparqlQuery(HttpMethod.POST, query) =>
      HttpRequest(
        method = HttpMethods.POST,
        // JC: QUERY_URI_PART is a string, can be concatenated directly
        uri = endpoint.path + s"$QUERY_URI_PART",
        Accept(`application/sparql-results+json`) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`.toContentType,
        s"$QUERY_PARAM_NAME=${sparql.statement.urlEncode}")

    case SparqlUpdate(HttpMethod.POST, update) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = endpoint.path + s"$UPDATE_URI_PART",
        makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`.toContentType,
        s"$UPDATE_PARAM_NAME=${sparql.statement.urlEncode}")
 }

  // JC: the meothdo name could be more specific
  private def makeRequestHeaders(endpoint: HttpEndpoint): List[HttpHeader] = {
    /* create the Basic authentication header */
    /* NOTE: Support for other authentication methods is not currently necessary, but could be added later */
    val auth: Option[Authorization] =
      endpoint
        .authentication
        .map {
          case BasicAuthentication(username, password) => Authorization(BasicHttpCredentials(username, password))
        }

    auth.toList
  }

  private def responseToResultSet(response: HttpResponse)(
    implicit
      _system: ActorSystem,
      _materializer: ActorMaterializer,
      _executionContext: ExecutionContext
  ): Future[ResultSet] = {

    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _)
        if entity.contentType.mediaType == `application/sparql-results+json` =>
        /* we need to override the content type, because the spray-json parser does not understand */
        /* anything but 'application/json' */
        Unmarshal(entity.withContentType(ContentTypes.`application/json`)).to[ResultSet]
     }
  }

  private def responseToBoolean(response: HttpResponse)(
    implicit
      _system: ActorSystem,
      _materializer: ActorMaterializer,
      _executionContext: ExecutionContext
  ): Future[Boolean] = {

    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _)
        if entity.contentType.mediaType == `text/boolean` =>
        Unmarshal(entity).to[Boolean]
      case HttpResponse(StatusCodes.OK, headers, entity, protocol) =>
        println(s"Unexpected response content type: ${entity.contentType} and/or media type: ${entity.contentType.mediaType}")
        Future.successful(true)

      case x@_ =>
        println(s"Unexpected response: $x")
        Future.successful(false)
    }

  }

  private def responseToSparqlResponse(response: HttpResponse): SparqlResponse = response match {
    case HttpResponse(StatusCodes.OK, _, _, _) =>
      // JC: can we also extract ResultSet here, instead of broadcast then zip?
      SparqlResponse(success = true)
    case HttpResponse(status, headers, entity, _) =>
      val error = SparqlClientRequestFailed(s"Request failed with: ${status}, headers: ${headers.mkString("|")}, message: ${entity})")
      SparqlResponse(success = false, error = Some(error))
    case x@_ =>
      val error = SparqlClientRequestFailed(s"Request failed with unexpected response: ${x})")
      SparqlResponse(success = false, error = Some(error))
  }


  /*           */
  /* CONSTANTS */
  /* --------- */
  // JC: these are same for all triple store??
  private val QUERY_URI_PART = "/query"
  private val QUERY_PARAM_NAME = "query"

  private val UPDATE_URI_PART = "/update"
  private val UPDATE_PARAM_NAME = "update"

  private val FORM_MIME_TYPE = "x-www-form-urlencoded"
  private val SPARQL_RESULTS_MIME_TYPE = "sparql-results+json"
  private val TEXT_BOOLEAN_MIME_TYPE = "boolean"

  /**
    * Media type for Form upload
    */
  private val `application/x-www-form-urlencoded` =
    MediaType.applicationWithFixedCharset(
      FORM_MIME_TYPE,
      HttpCharsets.`UTF-8`
    )

  /**
    * Media type for Sparql JSON protocol
    */
  private val `application/sparql-results+json` =
    MediaType.applicationWithFixedCharset(
      SPARQL_RESULTS_MIME_TYPE,
      HttpCharsets.`UTF-8`
    )

  /**
    * Media type for text/boolean
    */
  private val `text/boolean` =
    MediaType.text(TEXT_BOOLEAN_MIME_TYPE)

}
