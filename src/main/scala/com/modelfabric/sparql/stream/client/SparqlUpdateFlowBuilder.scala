package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, ZipWith}
import com.modelfabric.sparql.api.{SparqlClientRequestFailed, SparqlRequest, SparqlResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

trait SparqlUpdateFlowBuilder extends SparqlClientHelpers {

  import SparqlClientConstants._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val dispatcher: ExecutionContext

  def sparqlUpdateFlow(endpointFlow: HttpEndpointFlow[SparqlRequest]): Flow[SparqlRequest, SparqlResponse, NotUsed] = {

    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpointFlow.endpoint)).async.named("mapping.sparqlToHttpRequest"))
      val updateConnectionFlow = builder.add(endpointFlow.flow.async.named("http.sparqlUpdate"))
      val broadcastUpdateHttpResponse = builder.add(Broadcast[(Try[HttpResponse], SparqlRequest)](2).async.named("broadcast.updateResponse"))
      val booleanParser = builder.add(Flow[(Try[HttpResponse], SparqlRequest)].mapAsync(1)(res => responseToBoolean(res)).async.named("mapping.parseBoolean"))
      val resultMaker = builder.add(Flow.fromFunction(responseToSparqlResponse).async.named("mapping.makeResponseFromHeader"))
      val updateResultZipper = builder.add(ZipWith[Boolean, SparqlResponse, SparqlResponse]((success, response) =>
        response.copy(
          success = success
        )
      ).async.named("zipper.updateResultZipper"))

      converter ~> updateConnectionFlow ~> broadcastUpdateHttpResponse ~> booleanParser ~> updateResultZipper.in0
                                           broadcastUpdateHttpResponse ~> resultMaker   ~> updateResultZipper.in1

      FlowShape(converter.in, updateResultZipper.out)
    } named "flow.sparqlUpdateRequest")

  }

  /**
    * Consume the response entity and return a future boolean indicating the success status.
    * @param response
    * @return
    */
  protected def responseToBoolean(response: (Try[HttpResponse], _)): Future[Boolean] = {
    response match {
      case (Success(HttpResponse(status, _, entity, _)), _)
        if status == StatusCodes.OK && entity.contentType == `text/boolean` =>
        Unmarshal(entity).to[Boolean]
      case (Success(HttpResponse(status, _, entity, _)), _) if status == StatusCodes.OK =>
        entity.discardBytes()
        Future.successful(true)
      case (Success(HttpResponse(status, _, entity, _)), _) =>
        entity.discardBytes()
        Future.failed(SparqlClientRequestFailed(s"Unexpected response status: $status"))
      case x@_ =>
        Future.failed(SparqlClientRequestFailed(s"Unexpected response: $x"))
    }
  }



}
