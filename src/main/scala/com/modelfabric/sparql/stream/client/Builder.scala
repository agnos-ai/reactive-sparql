package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.{Outlet, Inlet, FlowShape}
import akka.stream.scaladsl._
import com.modelfabric.sparql.api.SparqlStatement
import com.modelfabric.sparql.spray.client.{ResultSet, QuerySolution}
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
  def flow(
    endpoint: HttpEndpoint,
    statements: Outlet[SparqlStatement],
    results: Inlet[ResultSet])(implicit _system: ActorSystem): FlowShape[SparqlStatement, ResultSet] = {

    import endpoint._

    val graph = GraphDSL.create() { implicit builder =>
       import GraphDSL.Implicits._

      val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
        Http().outgoingConnection(host, port).named("http.sparqlRequest")

      val converter = builder.add(Flow.fromFunction(sparqlToRequest).named("mapping.sparqlToHttpRequest"))
      val parser = builder.add(Flow.fromFunction(responseToResultSet).named("mapping.httpResponseToResultSet"))

      statements ~> converter ~> connectionFlow ~> parser ~> results

      FlowShape(converter.in, parser.out)

    }.named("flow.sparqlRequest")

    graph.shape
  }

  private def sparqlToRequest(sparql: SparqlStatement): HttpRequest = {
    // TODO: do the proper mapping
    HttpRequest()
  }

  private def responseToResultSet(response: HttpResponse): ResultSet = {
    // TODO: do the proper mapping
    ResultSet(null, null)
  }

}
