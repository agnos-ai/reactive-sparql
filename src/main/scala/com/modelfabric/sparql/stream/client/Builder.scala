package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials, Authorization}
import akka.http.scaladsl.model.{MediaType, HttpCharsets, HttpHeader, HttpResponse, HttpRequest, HttpMethods, StatusCodes, ContentTypes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Graph, FlowShape}
import akka.stream.scaladsl._
import com.modelfabric.extension.StringExtensions._
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.mapper.SparqlClientJsonProtocol._

import com.modelfabric.sparql.util.HttpEndpoint

import scala.concurrent.{ExecutionContext, Future}

object Builder {

  /**
    * Create a partial flow Graph of statements to results.
    *
    * @param endpoint the HTTP endpoint of the Sparql database server
    * @param _system the implicit actor system
    * @param _materializer the actor materializer
    * @param _executionContext the Futures execution context
    * @return
    */
  def sparqlRequestFlow(
    endpoint: HttpEndpoint
  )(implicit
      _system: ActorSystem,
      _materializer: ActorMaterializer,
      _executionContext: ExecutionContext
  ) : Graph[FlowShape[SparqlStatement, ResultSet], NotUsed] = {

    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      import endpoint._

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpoint)).named("mapping.sparqlToHttpRequest"))
      val connectionFlow = builder.add(Http().outgoingConnection(host, port).named("http.sparqlRequest"))
      val parser = builder.add(Flow[HttpResponse].mapAsync(1)(res => responseToResultSet(res)).named("mapping.parseResponse"))

      converter ~> connectionFlow ~> parser

      FlowShape(converter.in, parser.out)
    } named "flow.sparqlRequest"

  }

  private def sparqlToRequest(endpoint: HttpEndpoint)(sparql: SparqlStatement): HttpRequest = {
    makeHttpRequest(endpoint, sparql)
  }

  private def makeHttpRequest(endpoint: HttpEndpoint, sparql: SparqlStatement): HttpRequest = sparql match {

    case SparqlQuery(HttpMethod.GET, query) =>
      HttpRequest(
        method = HttpMethods.GET,
        uri = endpoint.path + s"$QUERY_URI_PART?$QUERY_PARAM_NAME=${sparql.statement.urlEncode}",
        makeRequestHeaders(endpoint)
      )

    case SparqlQuery(HttpMethod.POST, query) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = endpoint.path + s"$QUERY_URI_PART",
        makeRequestHeaders(endpoint)
      ).withEntity(`application/x-www-form-urlencoded`, s"$QUERY_PARAM_NAME=${sparql.statement.urlEncode}")

    case SparqlUpdate(HttpMethod.POST, update) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = endpoint.path + s"$UPDATE_URI_PART",
        makeRequestHeaders(endpoint)
      ).withEntity(`application/x-www-form-urlencoded`, s"$UPDATE_PARAM_NAME=${sparql.statement.urlEncode}")

 }

  private def makeRequestHeaders(endpoint: HttpEndpoint): List[HttpHeader] = {
    /* create the Basic authentication header */
    val auth: Option[Authorization] =
      endpoint
        .authentication
        .map(a => Authorization(BasicHttpCredentials(a.username, a.password)))

    /* add Accept: header to the request */
    Accept(`application/sparql-results+json`.mediaType) :: auth.toList
  }

  private def responseToResultSet(response: HttpResponse)(
    implicit
      _system: ActorSystem,
      _materializer: ActorMaterializer,
      _executionContext: ExecutionContext
  ): Future[ResultSet] = {

    response match {
      case response @ HttpResponse(StatusCodes.OK, _, entity, _)
        if entity.contentType.mediaType == `application/sparql-results+json`.mediaType =>
          /* we need to override the content type, because the spray-json parser does not understand */
          /* anything but 'application/json' */
          Unmarshal(entity.withContentType(ContentTypes.`application/json`)).to[ResultSet]
    }

  }

  /*           */
  /* CONSTANTS */
  /* --------- */

  private val QUERY_URI_PART = "/query"
  private val QUERY_PARAM_NAME = "query"

  private val UPDATE_URI_PART = "/update"
  private val UPDATE_PARAM_NAME = "update"

  private val FORM_MIME_TYPE = "x-www-form-urlencoded"
  private val SPARQL_RESULTS_MIME_TYPE = "sparql-results+json"

  /**
    * Media type for Form upload
    */
  private val `application/x-www-form-urlencoded` =
    MediaType.applicationWithFixedCharset(
      FORM_MIME_TYPE,
      HttpCharsets.`UTF-8`
    ).toContentType

  /**
    * Media type for Sparql JSON protocol
    */
  private val `application/sparql-results+json` =
    MediaType.applicationWithFixedCharset(
      SPARQL_RESULTS_MIME_TYPE,
      HttpCharsets.`UTF-8`
    ).toContentType

}
