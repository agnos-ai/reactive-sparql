package com.modelfabric.sparql.stream.client

import java.io.{StringReader, StringWriter}
import java.net.{URI, URL}
import java.nio.file.Path

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import com.modelfabric.sparql.api.{HttpMethod => ApiHttpMethod, _}
import com.modelfabric.sparql.util.HttpEndpoint
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._


object GraphStoreRequestFlowBuilder {

  /**
    * A Set of status codes on which the response will always show success = true
    */
  val successfulHttpResponseStatusCodes: Set[StatusCode] = {
    Set(
      StatusCodes.OK,
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

  /**
    * If this is set to true (default) then the response entity is "strictified", i.e. all chunks are loaded
    * into memory in one go.
    */
  val useStrictByteStringStrategy = true

  /**
    * How long to wait for a [[HttpEntity.Strict]] to appear when processing the [[HttpEntity]]
    */
  val strictEntityReadTimeout: FiniteDuration = 60 seconds

  def graphStoreRequestFlow(endpoint: HttpEndpoint): Flow[GraphStoreRequest, GraphStoreResponse, _] = {
    Flow
      .fromFunction(graphStoreOpToRequest(endpoint))
      .via(Http().cachedHostConnectionPool[GraphStoreRequest](endpoint.host, endpoint.port))
      .flatMapConcat {
      case (Success(response), request) =>
        val gsr = GraphStoreResponse(
          request,
          success = calculateSuccess(response.status),
          statusCode = response.status.intValue,
          statusText = response.status.reason
        )
        makeModelSource(response.entity).map( s => gsr.copy(model = s)).take(1)

      case (Failure(error), _) =>
        throw error //fail the stream, there is nothing we can do
    }
  }

  def makeModelSource(entity: HttpEntity): Source[Option[Model], Any] = {
    if ( entity.isKnownEmpty()
      || entity.contentType.mediaType != `application/n-quads`.mediaType) {
      entity.discardBytes()
      Source.single(None)
    } else if ( !useStrictByteStringStrategy) {
      // TODO: mapping over the data bytes stream won't work because the stream will never emit for empty entities
      entity.dataBytes
        .map { bs =>
          val reader = new StringReader(bs.utf8String)
          val mt = Try(Rio.parse(reader, "", RDFFormat.NQUADS))
          mt.toOption
        }
    } else if (useStrictByteStringStrategy) {
      // this workaround does seem to be alright, because chunked resonses have a limited size
      Source.single(entity.withoutSizeLimit())
        .mapAsync(numberOfCpuCores)(_.toStrict(strictEntityReadTimeout))
        .map { bs =>
          val reader = new StringReader(bs.data.utf8String)
          val mt = Try(Rio.parse(reader, "", RDFFormat.NQUADS))
          mt.toOption
        }
    } else {
      // unreachable code, but IntelliJ is dumb for not seeing that
      ???
    }
  }

  def calculateSuccess(statusCode: StatusCode): Boolean = {
    if (successfulHttpResponseStatusCodes.contains(statusCode)) true
    else if (failingHttpResponseStatusCodes.contains(statusCode)) false
    else {
      throw SparqlClientRequestFailed(s"request failed with status code: $statusCode")
    }
  }

  def graphStoreOpToRequest(endpoint: HttpEndpoint)
                           (graphStoreRequest: GraphStoreRequest): (HttpRequest, GraphStoreRequest) = {
    (makeHttpRequest(endpoint, graphStoreRequest), graphStoreRequest)
  }

  def makeHttpRequest(endpoint: HttpEndpoint, request: GraphStoreRequest): HttpRequest = {
    request match {

      case GetGraphM(graphUri, method) =>
        HttpRequest(
          method = mapHttpMethod(method),
          uri = s"${endpoint.path}${mapGraphOptionToPath(graphUri)}"
        ).withHeaders(
          Accept(`application/n-quads`.mediaType)
          :: makeRequestHeaders(endpoint)
        )

      case DropGraphM(graphUri, method) =>
        HttpRequest(
          method = mapHttpMethod(method),
          uri = s"${endpoint.path}${mapGraphOptionToPath(graphUri)}"
        ).withHeaders(makeRequestHeaders(endpoint))

      case InsertGraphFromModelM(model, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapGraphContentType(format)) {
          () => makeGraphSource(model, format)
        }

      case InsertGraphFromURLM(url, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapGraphContentType(format)) {
          () => makeGraphSource(url, format)
        }

      case InsertGraphFromPathM(path, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapGraphContentType(format)) {
          () => makeGraphSource(path, format)
        }
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
    )
    .withHeaders(makeRequestHeaders(endpoint))
    .withEntity(
      entity = HttpEntity(
        contentType = contentType,
        data = entitySourceCreator()
      )
    )
  }

  private def mapGraphOptionToPath(graphUri: Option[URI]): String = graphUri match {
    case Some(uri) => s"?$GRAPH_PARAM_NAME=${uri.toString.urlEncode}"
    case None      => s"?$DEFAULT_PARAM_NAME"

  }

  private def mapGraphContentType(format: RDFFormat): ContentType = format match {
    case f: RDFFormat if f == RDFFormat.NTRIPLES => `application/n-triples`
    case f: RDFFormat if f == RDFFormat.NQUADS   => `application/n-quads`
    case f: RDFFormat if f == RDFFormat.TURTLE   => `text/turtle`
    case f: RDFFormat if f == RDFFormat.JSONLD   => `application/ld+json`
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

}
