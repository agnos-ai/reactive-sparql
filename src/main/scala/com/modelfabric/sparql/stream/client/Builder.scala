package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, Authorization}
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.{Graph, FlowShape}
import akka.stream.scaladsl._
import com.modelfabric.sparql.api.SparqlStatement
import com.modelfabric.sparql.spray.client._
import com.modelfabric.sparql.util.HttpEndpoint

import scala.concurrent.Future

object Builder {

  /**
    * Create a partial flow Graph of statements to results.
    *
    * @param endpoint
    * @param _system
    * @return
    */
  def sparqlRequestFlow(
    endpoint: HttpEndpoint
  )(implicit _system: ActorSystem): Graph[FlowShape[SparqlStatement, ResultSet], NotUsed] = {

    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      import endpoint._

      val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
        Http().outgoingConnection(host, port).named("http.sparqlRequest")

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpoint)).named("mapping.sparqlToHttpRequest"))
      val parser = builder.add(Flow.fromFunction(responseToResultSet).named("mapping.httpResponseToResultSet"))

      converter ~> connectionFlow ~> parser

      FlowShape(converter.in, parser.out)
    }.named("flow.sparqlRequest")

  }

  private def sparqlToRequest(endpoint: HttpEndpoint)(sparql: SparqlStatement): HttpRequest = {
    // create the Basic authentication header
    val auth: Option[Authorization] =
      endpoint
        .authentication
        .map(a => Authorization(BasicHttpCredentials(a.username, a.password)))

    // TODO: do the proper mapping
    HttpRequest(uri = endpoint.path, headers = auth.toList)
  }

  private def responseToResultSet(response: HttpResponse): ResultSet = {
    // TODO: do the proper mapping
    ResultSet(
      head = ResultSetVars("status" :: "body" :: Nil),
      results = ResultSetResults(
        QuerySolution(Map(
          "status"  -> QuerySolutionValue("integer", Some("integer"), response.status.intValue().toString),
          "body"    -> QuerySolutionValue("string", Some("string"), response.entity.toString))
        ) :: Nil
      )
    )
  }

}
