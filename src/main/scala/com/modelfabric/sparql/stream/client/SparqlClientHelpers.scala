package com.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, PredefinedFromEntityUnmarshallers, Unmarshal}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.modelfabric.sparql.api.{HttpMethod => ApiHttpMethod, _}
import com.modelfabric.sparql.stream.client.SparqlClientConstants._
import com.modelfabric.sparql.util.{BasicAuthentication, HttpEndpoint}
import org.eclipse.rdf4j.rio.RDFFormat

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


trait SparqlClientHelpers {

  import com.modelfabric.extension.StringExtensions._
  implicit val rawBooleanFromEntityUnmarshaller: FromEntityUnmarshaller[Boolean] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.map(_.toBoolean)

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val dispatcher: ExecutionContext

  def pooledHttpClientFlow[T](endpoint: HttpEndpoint): Flow[(HttpRequest, T), (Try[HttpResponse], T), _] = {
    Http().cachedHostConnectionPool[T](endpoint.host, endpoint.port)
  }

  def sparqlToRequest(endpoint: HttpEndpoint)(request: SparqlRequest): (HttpRequest, SparqlRequest) = {
    (makeHttpRequest(endpoint, request.statement), request)
  }

  // JC: not good OO design. I think it's better to create a new class SparqlEndpoint
  // SSZ: not sure what you mean by that Jian? HttpEndpoint is BTW a class that abstracts spray/akka-http out of the
  // picture so the API is independent of the underlying implementation.
  def makeHttpRequest(endpoint: HttpEndpoint, sparql: SparqlStatement): HttpRequest = sparql match {
    case SparqlQuery(ApiHttpMethod.GET, query, _, reasoning,_) =>
      HttpRequest(
        method = HttpMethods.GET,
        uri = s"${endpoint.path}$QUERY_URI_PART?$QUERY_PARAM_NAME=${query.urlEncode}&$REASONING_PARAM_NAME=$reasoning",
        Accept(`application/sparql-results+json`.mediaType) :: makeRequestHeaders(endpoint)
      )

    case SparqlQuery(ApiHttpMethod.POST, query, _, reasoning, _) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"${endpoint.path}$QUERY_URI_PART",
        Accept(`application/sparql-results+json`.mediaType) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`,
        s"$QUERY_PARAM_NAME=${query.urlEncode}&$REASONING_PARAM_NAME=$reasoning"
      )

    case SparqlModelConstruct(ApiHttpMethod.POST, query, reasoning) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"${endpoint.path}$QUERY_URI_PART",
        Accept(`application/n-quads`.mediaType) :: makeRequestHeaders(endpoint)
        //Accept(`text/x-nquads`.mediaType) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`,
        s"$QUERY_PARAM_NAME=${query.urlEncode}&$REASONING_PARAM_NAME=$reasoning"
      )

    case SparqlUpdate(ApiHttpMethod.POST, update) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"${endpoint.path}$UPDATE_URI_PART",
        Accept(`text/boolean`.mediaType) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`,
        s"$UPDATE_PARAM_NAME=${update.urlEncode}")
  }

  // JC: the method name could be more specific
  //SSZ: this is on purpose generic as adding authentication is not the only thing that could be adding.
  def makeRequestHeaders(endpoint: HttpEndpoint): List[HttpHeader] = {
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

  def responseToSparqlResponse(response: (Try[HttpResponse], SparqlRequest)): SparqlResponse = response match {
    case (Success(HttpResponse(StatusCodes.OK, _, _, _)), request) =>
      SparqlResponse(request = request)
    case (Success(HttpResponse(status, headers, entity, _)), request) =>
      val error = SparqlClientRequestFailed(s"Request failed with: $status, headers: ${headers.mkString("|")}, message: $entity)")
      SparqlResponse(success = false, request = request, error = Some(error))
    case (Failure(throwable), request) =>
      val error = SparqlClientRequestFailedWithError("Request failed on the HTTP layer", throwable)
      SparqlResponse(success = false, request = request, error = Some(error))
  }

  protected def responseToBoolean(response: (Try[HttpResponse], _)): Future[Boolean] = {
    response match {
      case (Success(HttpResponse(status, _, entity, _)), _)
        if status == StatusCodes.OK && entity.contentType == `text/boolean` =>
        Unmarshal(entity).to[Boolean]
      case (Success(HttpResponse(status, _, _, _)), _) if status == StatusCodes.OK =>
        Future.successful(true)
      case (Success(HttpResponse(status, _, _, _)), _) =>
        Future.failed(SparqlClientRequestFailed(s"Unexpected response status: $status"))
      case x@_ =>
        Future.failed(SparqlClientRequestFailed(s"Unexpected response: $x"))
    }
  }

  protected def mapHttpMethod(in: ApiHttpMethod): HttpMethod = in match {
    case ApiHttpMethod.GET => HttpMethods.GET
    case ApiHttpMethod.POST => HttpMethods.POST
    case ApiHttpMethod.PUT => HttpMethods.PUT
    case ApiHttpMethod.DELETE => HttpMethods.DELETE
  }

  def mapRdfFormatToContentType(format: RDFFormat): ContentType = format match {
    case f: RDFFormat if f == RDFFormat.NTRIPLES =>
      `application/n-triples`
    case f: RDFFormat if f == RDFFormat.NQUADS   =>
      `application/n-quads`
    case f: RDFFormat if f == RDFFormat.TURTLE   =>
      `text/turtle`
    case f: RDFFormat if f == RDFFormat.JSONLD   =>
      `application/ld+json`
  }

  def mapContentTypeToRdfFormat(contentType: ContentType): RDFFormat = {
    //for some reason we need to compare the media types, content-type does not always work
    contentType.mediaType match {
      case format if format == `text/x-nquads`.mediaType =>
        RDFFormat.NQUADS
      case format if format == `application/n-quads`.mediaType =>
        RDFFormat.NQUADS
      case format if format == `text/turtle`.mediaType =>
        RDFFormat.TURTLE
      case format if format == `application/n-triples`.mediaType =>
        RDFFormat.NTRIPLES
      case format if format == `application/ld+json`.mediaType =>
        RDFFormat.JSONLD
      case format =>
        throw new IllegalArgumentException(s"unsupported Content-Type: $format")
    }
  }

}
