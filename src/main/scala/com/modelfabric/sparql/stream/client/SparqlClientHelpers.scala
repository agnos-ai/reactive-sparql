package com.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethod => _, _}
import akka.http.scaladsl.model.headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.SparqlClientConstants._
import com.modelfabric.sparql.util.{BasicAuthentication, HttpEndpoint}

import scala.util.{Failure, Success, Try}


trait SparqlClientHelpers {

  import com.modelfabric.extension.StringExtensions._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  def pooledHttpClientFlow(endpoint: HttpEndpoint): Flow[(HttpRequest, SparqlRequest), (Try[HttpResponse], SparqlRequest), _] = {
    Http().cachedHostConnectionPool[SparqlRequest](endpoint.host, endpoint.port)
  }

  def sparqlToRequest(endpoint: HttpEndpoint)(request: SparqlRequest): (HttpRequest, SparqlRequest) = {
    (makeHttpRequest(endpoint, request.statement), request)
  }

  // JC: not good OO design. I think it's better to create a new class SparqlEndpoint
  // SSZ: not sure what you mean by that Jian? HttpEndpoint is BTW a class that abstracts spray/akka-http out of the
  // picture so the API is independent of the underlying implementation.
  def makeHttpRequest(endpoint: HttpEndpoint, sparql: SparqlStatement): HttpRequest = sparql match {
    case SparqlQuery(HttpMethod.GET, query, _, reasoning,_) =>
      HttpRequest(
        method = HttpMethods.GET,
        uri = s"${endpoint.path}$QUERY_URI_PART?$QUERY_PARAM_NAME=${query.urlEncode}&$REASONING_PARAM_NAME=$reasoning",
        Accept(`application/sparql-results+json`.mediaType) :: makeRequestHeaders(endpoint)
      )

    case SparqlQuery(HttpMethod.POST, query, _, reasoning, _) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"${endpoint.path}$QUERY_URI_PART",
        Accept(`application/sparql-results+json`.mediaType) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`,
        s"$QUERY_PARAM_NAME=${query.urlEncode}&$REASONING_PARAM_NAME=$reasoning"
      )

    case SparqlModelConstruct(HttpMethod.POST, query, reasoning) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"${endpoint.path}$QUERY_URI_PART",
        Accept(`application/n-quads`.mediaType) :: makeRequestHeaders(endpoint)
        //Accept(`text/x-nquads`.mediaType) :: makeRequestHeaders(endpoint)
      ).withEntity(
        `application/x-www-form-urlencoded`,
        s"$QUERY_PARAM_NAME=${query.urlEncode}&$REASONING_PARAM_NAME=$reasoning"
      )

    case SparqlUpdate(HttpMethod.POST, update) =>
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
      SparqlResponse(success = true, request = request)
    case (Success(HttpResponse(status, headers, entity, _)), request) =>
      val error = SparqlClientRequestFailed(s"Request failed with: $status, headers: ${headers.mkString("|")}, message: $entity)")
      SparqlResponse(success = false, request = request, error = Some(error))
    case (Failure(throwable), request) =>
      val error = SparqlClientRequestFailedWithError("Request failed on the HTTP layer", throwable)
      SparqlResponse(success = false, request = request, error = Some(error))
  }

}
