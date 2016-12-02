package com.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl._
import com.modelfabric.extension.StringExtensions._
import com.modelfabric.sparql.api.HttpMethod
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.mapper.SparqlClientJsonProtocol._
import com.modelfabric.sparql.util.{BasicAuthentication, HttpEndpoint}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object SparqlFlowBuilder {
  /*           */
  /* CONSTANTS */
  /* --------- */
  // JC: these are same for all triple store??
  //SSZ: AFAIK yes: https://www.w3.org/TR/sparql11-protocol/
  private val QUERY_URI_PART = "/query"
  private val QUERY_PARAM_NAME = "query"
  private val REASONING_PARAM_NAME = "reasoning"

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


trait SparqlFlowBuilder {

  import SparqlFlowBuilder._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val dispatcher: ExecutionContext

 /**
    * Create a flow of Sparql requests to results.
    * {{{
    *
    * TODO
    *
    * }}}
    *
    * @param endpoint the HTTP endpoint of the Sparql triple store server
    * @return
    */
  def sparqlRequestFlow(
    endpoint: HttpEndpoint
  ): Flow[SparqlRequest, SparqlResponse, _] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val partition = builder.add(Partition[SparqlRequest](2, {
        case SparqlRequest(SparqlQuery(_,_,_,_)) => 0
        case SparqlRequest(SparqlUpdate(_,_)) => 1
      }))

      val responseMerger = builder.add(Merge[SparqlResponse](2).named("merge.sparqlResponse"))

      partition ~> sparqlQueryFlow(endpoint)  ~> responseMerger
      partition ~> sparqlUpdateFlow(endpoint) ~> responseMerger

      FlowShape(partition.in, responseMerger.out)

    } named "flow.sparqlRequestFlow")
  }

  /**
    * Create a flow of Sparql requests to results.
    * {{{
    *
    * TODO
    *
    * }}}
    *
    * @param endpoint the HTTP endpoint of the Sparql triple store server
    * @return
    */
  def sparqlQueryFlow(
    endpoint: HttpEndpoint
  ): Flow[SparqlRequest, SparqlResponse, _] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      import endpoint._

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpoint)).async.named("mapping.sparqlToHttpRequest"))
      val queryConnectionFlow = builder.add(Http().cachedHostConnectionPool[SparqlRequest](host, port).async.named("http.sparqlQueryConnection"))
      val broadcastQueryHttpResponse = builder.add(Broadcast[(Try[HttpResponse], SparqlRequest)](2).async.named("broadcast.queryResponse"))
      val resultSetParser = builder.add(Flow[(Try[HttpResponse], SparqlRequest)].mapAsync(1)(res => responseToResultSet(res)).async.named("mapping.parseResultSet"))
      val resultSetMapper = builder.add(Flow.fromFunction(resultSetToMappedResult).async.named("mapping.mapResultSet"))
      val resultMaker = builder.add(Flow.fromFunction(responseToSparqlResponse).async.named("mapping.makeResponseFromHeader"))
      val queryResultZipper = builder.add(ZipWith[List[SparqlResult], SparqlResponse, SparqlResponse](
        (result, response) =>
          response.copy(result = result)
      ).async.named("zipper.queryResultZipper"))

      converter ~> queryConnectionFlow ~> broadcastQueryHttpResponse ~> resultSetParser ~> resultSetMapper ~> queryResultZipper.in0
      broadcastQueryHttpResponse ~> resultMaker ~> queryResultZipper.in1

      FlowShape(converter.in, queryResultZipper.out)
    } named "flow.sparqlQueryRequest")
  }

  def sparqlUpdateFlow(
    endpoint: HttpEndpoint
  ): Flow[SparqlRequest, SparqlResponse, _] = {

    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      import endpoint._

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpoint)).async.named("mapping.sparqlToHttpRequest"))
      val updateConnectionFlow = builder.add(Http().cachedHostConnectionPool[SparqlRequest](host, port).async.named("http.sparqlUpdate"))
      val broadcastUpdateHttpResponse = builder.add(Broadcast[(Try[HttpResponse], SparqlRequest)](2).async.named("broadcast.updateResponse"))
      val booleanParser = builder.add(Flow[(Try[HttpResponse], SparqlRequest)].mapAsync(1)(res => responseToBoolean(res)).async.named("mapping.parseBoolean"))
      val resultMaker = builder.add(Flow.fromFunction(responseToSparqlResponse).async.named("mapping.makeResponseFromHeader"))
      val updateResultZipper = builder.add(ZipWith[Boolean, SparqlResponse, SparqlResponse]( (success, response) =>
        response.copy(
          success = success
        )
      ).async.named("zipper.updateResultZipper"))

      converter ~> updateConnectionFlow ~> broadcastUpdateHttpResponse ~> booleanParser ~> updateResultZipper.in0
                                           broadcastUpdateHttpResponse ~> resultMaker   ~> updateResultZipper.in1

      FlowShape(converter.in, updateResultZipper.out)
    } named "flow.sparqlUpdateRequest")

  }

  private def sparqlToRequest(endpoint: HttpEndpoint)(request: SparqlRequest): (HttpRequest, SparqlRequest) = {
    (makeHttpRequest(endpoint, request.statement), request)
  }

  // JC: not good OO design. I think it's better to create a new class SparqlEndpoint
  // SSZ: not sure what you mean by that Jian? HttpEndpoint is BTW a class that abstracts spray/akka-http out of the
  // picture so the API is independent of the underlying implementation.
  private def makeHttpRequest(endpoint: HttpEndpoint, sparql: SparqlStatement): HttpRequest = sparql match {
    case SparqlQuery(HttpMethod.GET, query, _, reasoning) =>
      HttpRequest(
        method = HttpMethods.GET,
        uri = s"${endpoint.path}$QUERY_URI_PART?$QUERY_PARAM_NAME=${sparql.statement.urlEncode}&$REASONING_PARAM_NAME=$reasoning",
        Accept(`application/sparql-results+json`) :: makeRequestHeaders(endpoint)
      )

    case SparqlQuery(HttpMethod.POST, query, _, reasoning) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"${endpoint.path}$QUERY_URI_PART",
        Accept(`application/sparql-results+json`) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`.toContentType,
        s"$QUERY_PARAM_NAME=${sparql.statement.urlEncode}&$REASONING_PARAM_NAME=$reasoning")

    case SparqlUpdate(HttpMethod.POST, update) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"${endpoint.path}$UPDATE_URI_PART",
        makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`.toContentType,
        s"$UPDATE_PARAM_NAME=${sparql.statement.urlEncode}")
 }

  // JC: the method name could be more specific
  //SSZ: this is on purpose generic as adding authentication is not the only thing that could be adding.
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

  private def responseToResultSet(response: (Try[HttpResponse], SparqlRequest)): Future[(ResultSet, SparqlRequest)] = {
    response match {
      case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), request)
        if entity.contentType.mediaType == `application/sparql-results+json` =>
        /* we need to override the content type, because the spray-json parser does not understand */
        /* anything but 'application/json' */
        Unmarshal(entity.withContentType(ContentTypes.`application/json`)).to[ResultSet] map {
          (_, request)
        }
     }
  }

  private def resultSetToMappedResult(resultSet: (ResultSet, SparqlRequest))(
    implicit
    _system: ActorSystem,
    _materializer: ActorMaterializer,
    _executionContext: ExecutionContext
  ): List[SparqlResult] = {

    resultSet match {
      case (r, SparqlRequest(SparqlQuery(_,_,mapper,_))) =>
        mapper
          .map(r)
          .asInstanceOf[List[SparqlResult]]
    }

  }


  private def responseToBoolean(response: (Try[HttpResponse], SparqlRequest)): Future[Boolean] = {
    response match {
      case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _)
        if entity.contentType.mediaType == `text/boolean` =>
        Unmarshal(entity).to[Boolean]
      case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _) =>
        println(s"Unexpected response content type: ${entity.contentType} and/or media type: ${entity.contentType.mediaType}")
        Future.successful(true)
      case (Success(HttpResponse(status, _, _, _)), _) =>
        println(s"Unexpected response status: $status")
        Future.successful(true)
      case x@_ =>
        println(s"Unexpected response: $x")
        Future.successful(false)
    }

  }


  private def responseToSparqlResponse(response: (Try[HttpResponse], SparqlRequest)): SparqlResponse = response match {
    case (Success(HttpResponse(StatusCodes.OK, _, _, _)), request) =>
      SparqlResponse(success = true, request = request)
    case (Success(HttpResponse(status, headers, entity, _)), request) =>
      val error = SparqlClientRequestFailed(s"Request failed with: $status, headers: ${headers.mkString("|")}, message: $entity)")
      SparqlResponse(success = false, request = request, error = Some(error))
    case (Failure(throwable), request) =>
      val error = SparqlClientRequestFailedWithError("Request failed on the HTTP layer", throwable)
      SparqlResponse(success = false, request = request, error = Some(error))
  }
}
