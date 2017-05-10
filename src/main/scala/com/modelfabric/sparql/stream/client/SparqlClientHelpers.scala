package com.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, PredefinedFromEntityUnmarshallers}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.SparqlClientConstants._
import com.modelfabric.sparql.util.{BasicAuthentication, HttpEndpoint}
import org.eclipse.rdf4j.rio.RDFFormat

import scala.util.{Failure, Success, Try}


trait SparqlClientHelpers {

  import HttpMethods._

  import com.modelfabric.extension.StringExtensions._
  import com.modelfabric.sparql.util.SparqlQueryStringConverter._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  implicit val rawBooleanFromEntityUnmarshaller: FromEntityUnmarshaller[Boolean] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.map(_.toBoolean)

  def pooledHttpClientFlow[T](endpoint: HttpEndpoint): Flow[(HttpRequest, T), (Try[HttpResponse], T), _] = {
    Flow[(HttpRequest, T)]
      .log("beforeHttpRequest")
      .via(Http().cachedHostConnectionPool[T](endpoint.host, endpoint.port))
      .log("afterHttpRequest")
  }

  def sparqlToRequest(endpoint: HttpEndpoint)(request: SparqlRequest): (HttpRequest, SparqlRequest) = {
    (makeHttpRequest(endpoint, request.statement), request)
  }

  def acceptQueryMediaType(queryType: QueryType): MediaType = {
    val ct = queryType match {
      case StreamedQuery(contentType) => contentType
      case _: MappedQuery[_] => `application/sparql-results+json`
    }
    ct.mediaType
  }

  def makeHttpRequest(endpoint: HttpEndpoint, statement: SparqlStatement): HttpRequest = statement match {

    case query@SparqlQuery(_, _, queryType, _, _, _, _, _, _, _) if query.queryHttpMethod == GET =>
      HttpRequest(
        method = GET,
        uri = endpoint.path + "/query?" + query.encodedQueryString,
        Accept(acceptQueryMediaType(queryType)) :: makeRequestHeaders(endpoint),
        entity = HttpEntity.Empty.withContentType(`application/x-www-form-urlencoded`)
      )

    case query@SparqlQuery(_, _, queryType, _, _, _, _, _, _, _) if query.queryHttpMethod == POST =>
      HttpRequest(
        method = POST,
        uri = endpoint.path + "/query",
        Accept(acceptQueryMediaType(queryType)) :: makeRequestHeaders(endpoint),
        entity = HttpEntity(query.encodedQueryString).withContentType(`application/x-www-form-urlencoded`)
      )

    case SparqlModelConstruct(POST, query, reasoning) =>
      HttpRequest(
        method = POST,
        uri = s"${endpoint.path}$QUERY_URI_PART",
        Accept(`application/n-quads`.mediaType) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`,
        s"$QUERY_PARAM_NAME=${query.urlEncode}&$REASONING_PARAM_NAME=$reasoning"
      )

    case SparqlUpdate(POST, update) =>
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

  /**
    * Takes the HttpResponse and makes a SparqlResponse.
    *
    * NB: This function will NOT consume the response entity!!
    *
    * @param response
    * @return
    */
  def responseToSparqlResponse(response: (Try[HttpResponse], SparqlRequest)): SparqlResponse = response match {
    case (Success(HttpResponse(StatusCodes.OK, _, _, _)), request) =>
      SparqlResponse(request = request)
    case (Success(HttpResponse(status, headers, _, _)), request) =>
      val error = SparqlClientRequestFailed(s"Request failed with: $status, headers: ${headers.mkString("|")}")
      SparqlResponse(success = false, request = request, error = Some(error))
    case (Failure(throwable), request) =>
      val error = SparqlClientRequestFailedWithError("Request failed on the HTTP layer", throwable)
      SparqlResponse(success = false, request = request, error = Some(error))
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
      case format if format == `application/octet-stream`.mediaType =>
        system.log.warning("got application/octet-stream, assuming this is a chunked response containing NTRIPLES payload")
        RDFFormat.NTRIPLES
      case format =>
        throw new IllegalArgumentException(s"unsupported Content-Type: $format")
    }
  }

  def isSparqlResultsJson(contentType: ContentType): Boolean = {
    contentType.mediaType == `application/sparql-results+json`.mediaType
  }

}
