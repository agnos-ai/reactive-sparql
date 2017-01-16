package com.modelfabric.sparql.spray.client

import akka.actor.{ Props, ActorRef, ActorContext }
import akka.event.Logging

object SparqlClientFactory {

  /**
   * Create a SparqlClient with the specified type (see SparqlClientConfig)
   */
  def apply(
    context_ : ActorContext,
    config_ : SparqlClientConfig) : ActorRef = {

    val actorName = "sparql-client"
    val log = Logging(context_.system, SparqlClientFactory.getClass())

    log.info("Creating Spray SPARQL Client")

    context_.actorOf(
      Props(classOf[SparqlHttpSprayClient], config_), name = actorName
    )
  }
}
