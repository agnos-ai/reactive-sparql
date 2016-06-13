package com.modelfabric.sparql.client

import akka.actor.{ Props, ActorRef, ActorContext }
import akka.event.Logging

object SparqlClientFactory {

  import SparqlClientType._

  /**
   * Create a SparqlClient with the specified type (see SparqlClientConfig)
   */
  def apply(
    context_ : ActorContext,
    config_ : SparqlClientConfig) : ActorRef = {

    val actorName = "sparql-client"
    val log = Logging(context_.system, SparqlClientFactory.getClass())

    log.info("Creating SPARQL Client of Type {}", config_.clientType)

    val client = config_.clientType match {
      case HttpSpray ⇒ {
        context_.actorOf(
          Props(classOf[SparqlHttpSprayClient], config_), name = actorName
        )
      }
      //      case HttpJena    ⇒ {
      //        context_.actorOf(
      //          Props(new SparqlHttpJenaClient(config_)), name = actorName
      //        )
      //      }
      /*
      case StardogJena ⇒ {
        context_.actorOf(
          Props(new SparqlStardogClient(config_)), name = actorName
        )
      } */
      case _ ⇒ {
        log.error("Unknown SPARQL Client Type {}", config_.clientType)
        null
      }
    }
    client
  }
}
