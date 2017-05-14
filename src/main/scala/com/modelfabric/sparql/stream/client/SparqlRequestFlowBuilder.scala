package com.modelfabric.sparql.stream.client

import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import com.modelfabric.sparql.api.{SparqlModelConstruct, _}
import com.modelfabric.sparql.util.HttpEndpoint


trait SparqlRequestFlowBuilder
  extends SparqlQueryFlowBuilder with SparqlConstructToModelFlowBuilder with SparqlUpdateFlowBuilder {

  /**
    * Create a flow of Sparql requests to results.
    *
    * @param endpoint the HTTP endpoint of the Sparql triple store server
    * @return
    */
  def sparqlRequestFlow(
    endpoint: HttpEndpoint
  ): Flow[SparqlRequest, SparqlResponse, _] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val routes = 3

      val partition = builder.add(Partition[SparqlRequest](routes, {
        case SparqlRequest(_: SparqlQuery)          => 0
        case SparqlRequest(_: SparqlUpdate)         => 1
        case SparqlRequest(_: SparqlModelConstruct) => 2
      }))

      val responseMerger = builder.add(Merge[SparqlResponse](routes).named("merge.sparqlResponse"))

      partition ~> sparqlQueryFlow(endpoint)          ~> responseMerger
      partition ~> sparqlUpdateFlow(endpoint)         ~> responseMerger
      partition ~> sparqlModelConstructFlow(endpoint) ~> responseMerger

      FlowShape(partition.in, responseMerger.out)

    } named "flow.sparqlRequestFlow")
  }

}
