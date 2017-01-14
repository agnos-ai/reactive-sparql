package com.modelfabric.sparql.stream.client

import java.io.StringWriter
import java.net.{URI, URL}
import java.nio.file.Path

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import com.modelfabric.sparql.api.{HttpMethod => ApiHttpMethod, _}
import com.modelfabric.sparql.util.HttpEndpoint
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import scala.concurrent.Future
import scala.util.Try


object GraphStoreRequestFlowBuilder {

  /**
    * A Set of status codes on which the response will always show success = true
    */
  val successfulHttpResponseStatusCodes: Set[StatusCode] = {
    Set(
      StatusCodes.Created,
      StatusCodes.Accepted,
      StatusCodes.NoContent,
      StatusCodes.AlreadyReported
    )
  }

  /**
    * A Set of status codes which the flow can handle gracefully, even though
    * these all mean that the operation has failed. In all these cases, however
    * the stream remains open. Any codes not shown on the success or failure
    * list will cause the stream to fail and complete prematurely.
    */
  val failingHttpResponseStatusCodes: Set[StatusCode] = {
    Set(
      StatusCodes.NotFound,
      StatusCodes.Unauthorized,
      StatusCodes.PaymentRequired,
      StatusCodes.Forbidden,
      StatusCodes.NotFound,
      StatusCodes.ProxyAuthenticationRequired,
      StatusCodes.RequestTimeout ,
      StatusCodes.Conflict,
      StatusCodes.Gone
    )
  }
}


trait GraphStoreRequestFlowBuilder extends SparqlClientHelpers {

  import SparqlClientConstants._
  import com.modelfabric.extension.StringExtensions._

  import GraphStoreRequestFlowBuilder._

  def graphStoreRequestFlow(
    endpoint: HttpEndpoint
  ): Flow[GraphStoreRequest, GraphStoreResponse, _] = {
      Flow.fromFunction(graphStoreOpToRequest(endpoint))
        .via(Http().cachedHostConnectionPool[GraphStoreRequest](endpoint.host, endpoint.port))
        .mapAsync(numberOfCpuCores)(s => responseToResult(s._1, s._2))
  }

  def graphStoreOpToRequest(endpoint: HttpEndpoint)(graphStoreRequest: GraphStoreRequest): (HttpRequest, GraphStoreRequest) = {
    (makeHttpRequest(endpoint, graphStoreRequest), graphStoreRequest)
  }

  def makeHttpRequest(endpoint: HttpEndpoint, request: GraphStoreRequest): HttpRequest = {
    request match {
      case DropGraph(graphUri, ApiHttpMethod.DELETE) =>
        HttpRequest(
          method = HttpMethods.DELETE,
          uri = s"${endpoint.path}${mapGraphOptionToPath(graphUri)}"
        )
      case InsertGraphFromModel(model, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapGraphContentType(format)) {
          () => makeGraphSource(model, format)
        }
      case InsertGraphFromURL(url, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapGraphContentType(format)) {
          () => makeGraphSource(url, format)
        }
/*
      case InsertGraphFromPath(path, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapGraphContentType(format)) {
          () => makeGraphSource(path, format)
        }
*/
    }
  }

  private def makeInsertGraphHttpRequest
  (
    endpoint: HttpEndpoint,
    method: ApiHttpMethod,
    graphUri: Option[URI],
    contentType: ContentType
  )
  (
    entitySourceCreator: () => Source[ByteString, Any]
  ): HttpRequest = {
    HttpRequest(
      method = mapHttpMethod(method),
      uri = s"${endpoint.path}${mapGraphOptionToPath(graphUri)}"
    ).withEntity(
      entity = HttpEntity(
        contentType = contentType,
        data = entitySourceCreator()
      )
    )
  }

  private def mapGraphOptionToPath(graphUri: Option[URI]): String = graphUri match {
    case Some(uri) => s"?${GRAPH_PARAM_NAME}=${uri.toString.urlEncode}"
    case None      => s"?$DEFAULT_PARAM_NAME"

  }

  private def mapGraphContentType(format: RDFFormat): ContentType = format match {
    case f: RDFFormat if f == RDFFormat.NQUADS => `application/n-quads`
    case f: RDFFormat if f == RDFFormat.TURTLE => `text/turtle`
    case f: RDFFormat if f == RDFFormat.JSONLD => `application/ld+json`
  }

  private def makeGraphSource(model: Model, format: RDFFormat): Source[ByteString, Any] = {
    Source.single(model)
     .map { model =>
        val writer = new StringWriter()
        Rio.write(model, writer, format)
        ByteString(writer.getBuffer.toString, "UTF-8")
      }
  }

  private def makeGraphSource(fileUrl: URL, format: RDFFormat): Source[ByteString, Any] = {
    Source.single(Uri(fileUrl.toURI.toString))
      .mapAsync(1)(uri => Http().singleRequest(HttpRequest(uri = uri)))
      .flatMapConcat(res => res.entity.dataBytes)
  }

  private def makeGraphSource(filePath: Path, format: RDFFormat): Source[ByteString, Any] = {

    FileIO.fromPath(filePath)
  }

  private def responseToResult(response: Try[HttpResponse], request: GraphStoreRequest): Future[GraphStoreResponse] = {
    responseToBoolean((response, request), successfulHttpResponseStatusCodes, failingHttpResponseStatusCodes)
      .map( result => GraphStoreResponse(request, success = result))
  }

}
