package com.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl._
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.mapper.SparqlClientJsonProtocol._
import com.modelfabric.sparql.util.HttpEndpoint

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}


trait SparqlQueryToResultsFlowBuilder extends SparqlClientHelpers {

  import SparqlClientConstants._

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
                                          broadcastQueryHttpResponse ~>                    resultMaker     ~> queryResultZipper.in1

      FlowShape(converter.in, queryResultZipper.out)
    } named "flow.sparqlQueryRequest")
  }

  private def responseToResultSet(response: (Try[HttpResponse], SparqlRequest)): Future[(ResultSet, SparqlRequest)] = {
    response match {
      case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), request)
        if entity.contentType == `application/sparql-results+json` =>
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

}
