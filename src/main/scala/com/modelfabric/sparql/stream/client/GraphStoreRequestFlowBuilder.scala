package com.modelfabric.sparql.stream.client

import java.io.{StringReader, StringWriter}
import java.net.URL
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Flow, Framing, Source}
import akka.util.ByteString
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.util.HttpEndpoint
import org.eclipse.rdf4j.model.{IRI, Model}
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

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  import system.dispatcher

  /**
    * If this is set to true then the response entity is "strictified", i.e. all chunks are loaded
    * into memory in one go. However by being false, the result is processed
    * as a proper stream but there is the risk is that the user might
    * get more than a single response per request.
    */
  val useStrictByteStringStrategy = true

  /**
    * Specifies for how long to wait for a "strict" http entity.
    */
  val strictEntityReadTimeout: FiniteDuration = 60 seconds

  /**
    * Limit the response entity size to 100MB by default
    */
  val strictEntityMaximumLengthInBytes: Int = 100 * 1024 * 1024

  def graphStoreRequestFlow(endpoint: HttpEndpoint): Flow[GraphStoreRequest, GraphStoreResponse, _] = {
    Flow
      .fromFunction(graphStoreOpToRequest(endpoint))
      .log("beforeHttpRequest")
      .via(pooledHttpClientFlow[GraphStoreRequest](endpoint))
      .log("afterHttpRequest")
      .flatMapConcat {
        case (Success(response), request) =>
          val gsr = GraphStoreResponse(
            request,
            success = calculateSuccess(response.status),
            statusCode = response.status.intValue,
            statusText = response.status.reason
          )
          makeModelSource(response.entity).map( s => gsr.copy(model = s))
      case (Failure(error), _) =>
        throw error //fail the stream, there is nothing we can do
    }
  }

  def makeModelSource(entity: HttpEntity): Source[Option[Model], Any] = {
    if ( !entity.isChunked() && (entity.isKnownEmpty() || entity.contentLengthOption.getOrElse(0) == 0)) {
      // if we know there are no bytes in the entity (no-graph has been returned)
      // or the reponse content type is not what we have requested then no model is emitted.
      entity.discardBytes()
      Source.single(None)
    } else if ( !useStrictByteStringStrategy && entity.isChunked()) {
      // NB: mapping over the data bytes stream won't work because the stream will never emit for empty entities
      // the trick is to introduce a scan() call, which will emit an empty string even if nothing comes through.
      entity.withoutSizeLimit().dataBytes
        .via(Framing.delimiter(ByteString.fromString("\n"), maximumFrameLength = strictEntityMaximumLengthInBytes, allowTruncation = true))
        .scan(ByteString.empty)((a,b) => b ++ a)
        .filter(_.nonEmpty)
        .map { bs =>
          if ( !bs.isEmpty ) {
            val reader = new StringReader(bs.utf8String)
            val mt = Try(Rio.parse(reader, "", mapContentTypeToRdfFormat(entity.contentType)))
            mt.toOption
          } else {
            None
          }
        }
    } else { //i.e. if useStrictByteStringStrategy is true
      // this workaround does seem to be alright for smaller graphs that can be
      // converted to a strict in-memory entity - currently this off by default
      Source.single(entity.withSizeLimit(strictEntityMaximumLengthInBytes))
        .mapAsync(numberOfCpuCores)(_.toStrict(strictEntityReadTimeout))
        .map { bs =>
          val reader = new StringReader(bs.data.utf8String)
          val mt = Try(Rio.parse(reader, "", mapContentTypeToRdfFormat(entity.contentType)))
          mt.toOption
        }
    }
  }

  /**
    * Returns true or false if a supported success or failure code is given. For unsupported
    * codes, a SparqlClientRequestFailed is thrown.
    *
    * @param statusCode
    * @return
    */
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
          method, uri = s"${endpoint.path}${mapGraphOptionToPath(graphUri)}"
        ).withHeaders(
          Accept(`application/n-triples`.mediaType)
          :: makeRequestHeaders(endpoint)
        )

      case DropGraphM(graphUri, method) =>
        HttpRequest(
          method, uri = s"${endpoint.path}${mapGraphOptionToPath(graphUri)}"
        ).withHeaders(makeRequestHeaders(endpoint))

      case InsertGraphFromModelM(model, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapRdfFormatToContentType(format)) {
          () => makeGraphSource(model, format)
        }

      case InsertGraphFromURLM(url, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapRdfFormatToContentType(format)) {
          () => makeGraphSource(url, format)
        }

      case InsertGraphFromPathM(path, format, graphUri, method) =>
        makeInsertGraphHttpRequest(endpoint, method, graphUri, mapRdfFormatToContentType(format)) {
          () => makeGraphSource(path, format)
        }
    }
  }

  private def makeInsertGraphHttpRequest
  (
    endpoint: HttpEndpoint,
    method: HttpMethod,
    graphIri: Option[IRI],
    contentType: ContentType
  )
  (
    entitySourceCreator: () => Source[ByteString, Any]
  ): HttpRequest = {
    HttpRequest(
      method, uri = s"${endpoint.path}${mapGraphOptionToPath(graphIri)}"
    )
    .withHeaders(makeRequestHeaders(endpoint))
    .withEntity(
      entity = HttpEntity(
        contentType = contentType,
        data = entitySourceCreator()
      )
    )
  }

  private def mapGraphOptionToPath(graphIri: Option[IRI]): String = graphIri match {
    case Some(uri) => s"?$GRAPH_PARAM_NAME=${uri.toString.urlEncode}"
    case None      => s"?$DEFAULT_PARAM_NAME"

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
      .mapAsync(1)(uri => Http().singleRequest {
        HttpRequest(uri = uri).withHeaders(Accept(mapRdfFormatToContentType(format).mediaType))
      })
      .flatMapConcat(res => res.entity.dataBytes)
  }

  private def makeGraphSource(filePath: Path, format: RDFFormat): Source[ByteString, Any] = {
    FileIO.fromPath(filePath)
  }

}
