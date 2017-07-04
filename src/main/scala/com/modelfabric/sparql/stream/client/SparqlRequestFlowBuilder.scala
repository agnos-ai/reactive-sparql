package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import com.modelfabric.sparql.api.{SparqlModelConstruct, _}


trait SparqlRequestFlowBuilder extends SparqlQueryFlowBuilder
  with SparqlConstructToModelFlowBuilder
  with SparqlUpdateFlowBuilder {

  /**
    * Create a flow of Sparql requests to results.
    *
    * @param endpointFlow the HTTP endpoint flow for the Sparql triple store server
    * @return
    */
  def sparqlRequestFlow(endpointFlow: HttpEndpointFlow[SparqlRequest]): Flow[SparqlRequest, SparqlResponse, NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val routes = 3

      val partition = builder.add(Partition[SparqlRequest](routes, {
        case SparqlRequest(_: SparqlQuery)          => 0
        case SparqlRequest(_: SparqlUpdate)         => 1
        case SparqlRequest(_: SparqlModelConstruct) => 2
      }))

      val responseMerger = builder.add(Merge[SparqlResponse](routes).named("merge.sparqlResponse"))

      partition ~> sparqlQueryFlow(endpointFlow)          ~> responseMerger
      partition ~> sparqlUpdateFlow(endpointFlow)         ~> responseMerger
      partition ~> sparqlModelConstructFlow(endpointFlow) ~> responseMerger

      FlowShape(partition.in, responseMerger.out)

    } named "flow.sparqlRequestFlow")
  }

}
