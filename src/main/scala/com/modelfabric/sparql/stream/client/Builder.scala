package com.modelfabric.sparql.stream.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, Authorization}
import akka.http.scaladsl.model.{HttpHeader, Uri, HttpResponse, HttpRequest}
import akka.stream.{Outlet, Inlet, FlowShape}
import akka.stream.scaladsl._
import com.modelfabric.sparql.api.SparqlStatement
import com.modelfabric.sparql.spray.client._
import com.modelfabric.sparql.util.HttpEndpoint

import scala.concurrent.Future

object Builder {

  /**
    * Create a Flow of statements to results.
    *
    * @param endpoint
    * @param statements
    * @param results
    * @param _system
    * @return
    */
  def sparqlRequestFlow(
    endpoint: HttpEndpoint,
    statements: Outlet[SparqlStatement],
    results: Inlet[ResultSet])(implicit _system: ActorSystem): FlowShape[SparqlStatement, ResultSet] = {

    val graph = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      import endpoint._

      val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
        Http().outgoingConnection(host, port).named("http.sparqlRequest")

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpoint)).named("mapping.sparqlToHttpRequest"))
      val parser = builder.add(Flow.fromFunction(responseToResultSet).named("mapping.httpResponseToResultSet"))

      statements ~> converter ~> connectionFlow ~> parser ~> results

      FlowShape(converter.in, parser.out)

    }.named("flow.sparqlRequest")

    graph.shape
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
      head = ResultSetVars(Nil),
      results = ResultSetResults(
        QuerySolution(Map(
          "status"  -> QuerySolutionValue("integer", Some("integer"), response.status.intValue().toString),
          "body"    -> QuerySolutionValue("string", Some("string"), response.entity.toString))
        ) :: Nil
      )
    )
  }

}
