package com.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, ZipWith}
import com.modelfabric.sparql.api.{SparqlRequest, SparqlResponse}
import com.modelfabric.sparql.util.HttpEndpoint

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

import com.modelfabric.sparql.mapper.SparqlClientJsonProtocol._


trait SparqlUpdateFlowBuilder extends SparqlClientHelpers {

  import SparqlClientConstants._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val dispatcher: ExecutionContext

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

  private def responseToBoolean(response: (Try[HttpResponse], SparqlRequest)): Future[Boolean] = {
    response match {
      case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _)
        if entity.contentType == `text/boolean` =>
        Unmarshal(entity).to[Boolean]
      case (Success(HttpResponse(StatusCodes.OK, _, _, _)), _) =>
        //println(s"WARING: Unexpected response content type: ${entity.contentType} and/or media type: ${entity.contentType.mediaType}")
        Future.successful(true)
      case (Success(HttpResponse(status, _, _, _)), _) =>
        println()
        Future.failed(new IllegalArgumentException(s"Unexpected response status: $status"))
      case x@_ =>
        println(s"Unexpected response: $x")
        Future.failed(new IllegalArgumentException(s"Unexpected response: $x"))
    }
  }

}
