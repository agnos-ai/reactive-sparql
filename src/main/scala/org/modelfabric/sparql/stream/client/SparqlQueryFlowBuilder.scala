package org.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, Source}
import akka.util.ByteString
import org.modelfabric.sparql.api._

import scala.util.{Failure, Success, Try}
import spray.json._

import scala.concurrent.ExecutionContext


trait SparqlQueryFlowBuilder extends SparqlClientHelpers {

  import org.modelfabric.sparql.mapper.SparqlClientJsonProtocol._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val dispatcher: ExecutionContext

  def sparqlQueryFlow(endpointFlow: HttpEndpointFlow[SparqlRequest]): Flow[SparqlRequest, SparqlResponse, Any] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val routes = 2

      val partition = builder.add(Partition[SparqlRequest](routes, {
        case SparqlRequest(SparqlQuery(_, _, StreamedQuery(_),_,_,_,_,_,_,_), _) => 0
        case SparqlRequest(SparqlQuery(_, _,_: MappedQuery[_],_,_,_,_,_,_,_), _) => 1
      }))

      val responseMerger = builder.add(Merge[SparqlResponse](routes).named("merge.sparqlResponse"))

      partition.out(0) ~> sparqlQueryToStreamFlow(endpointFlow)                               ~> responseMerger
      partition.out(1) ~> sparqlQueryToStreamFlow(endpointFlow) ~> responseUnmarshallerFlow() ~> responseMerger

      FlowShape(partition.in, responseMerger.out)

    } named "flow.sparqlRequestFlow")

  }

  private def responseUnmarshallerFlow(): Flow[SparqlResponse, SparqlResponse, Any] = {
    Flow[SparqlResponse]
      .flatMapConcat {

        case response@SparqlResponse(
          SparqlRequest(
            SparqlQuery(_, _, mappedQueryType: MappedQuery[_],_,_,_,_,_,_,_), _
          ), true, _, List(StreamingSparqlResult(dataStream, Some(contentType))), _)
        if isSparqlResultsJson(contentType) =>
          Source.fromFuture {
            dataStream.runFold(ByteString.empty)(_ ++ _).map { data =>
              Try(format3.read(data.utf8String.parseJson)) match {
                case Success(x: ResultSet) =>
                  response.copy(result = mappedQueryType.mapper.map(x))
                case Failure(err) =>
                  response.copy(
                    success = false, result = Nil,
                    error = Some(SparqlClientRequestFailedWithError("failed to un-marshall result", err))
                  )
              }
            }
          }

        // catchall for all streaming responses, we need to process the response entities stream, otherwise
        // we will get back-pressure issues.
        case response@SparqlResponse(
          _, _, _, List(StreamingSparqlResult(dataStream, contentType)), _) =>
          dataStream.map { data =>
            response.copy(
              success = false, result = Nil,
              error = Some(SparqlClientRequestFailed(
                s"unsupported result type [${contentType.getOrElse("Unknown")}] ${data.take(100).utf8String}...")
              )
            )
          }

        // we don't care about errors
        case r: SparqlResponse =>
          Source.single(r.copy(
            success = false, result = Nil,
            error = Some(SparqlClientRequestFailed(s"invalid request"))
          ))
      }
  }

  private def sparqlQueryToStreamFlow(endpointFlow: HttpEndpointFlow[SparqlRequest]): Flow[SparqlRequest, SparqlResponse, Any] = {

    Flow[SparqlRequest]
      .map {
        case request@SparqlRequest(query: SparqlQuery, _) =>
          (makeHttpRequest(endpointFlow.endpoint, query), request)
        }
      .log("SPARQL endpoint request")
      .via(endpointFlow.flow)
      .log("SPARQL endpoint response")
      .map {
        case (Success(HttpResponse(status, _, entity, _)), request) =>
          SparqlResponse(request, status == StatusCodes.OK, status, result = StreamingSparqlResult(entity.dataBytes, Some(entity.contentType)) :: Nil)
        case (Failure(error), request) =>
          SparqlResponse(request, success = false, error = Some(SparqlClientRequestFailedWithError("failed to execute sparql query", error)))
      }

  }

}
