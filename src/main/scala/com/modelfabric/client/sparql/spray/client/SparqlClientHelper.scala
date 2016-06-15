package com.modelfabric.client.sparql.spray.client

import akka.actor.ActorContext
import com.modelfabric.client.sparql.spray.PrefixMapping
import scala.util.{Failure, Try}
import akka.actor.Status.Success

/**
 * A Helper Trait with a connection to a SPARQL endpoint
 */
trait SparqlClientHelper {

  protected def context : ActorContext

  val defaultSparqlClientConfig = SparqlClientConfig(context.system)

  /**
   * Re-implement pm to pass in your own prefix mappings
   */
  implicit protected val pm : PrefixMapping

  /**
   * Override this to pass in your own endpoint URL
   */
  protected val sparqlEndPoint : Option[String] = defaultSparqlClientConfig.endPoint

  /**
   * You could override this to pass in your own SparqlClientConfig
   */
  protected lazy val sparqlClientConfig = defaultSparqlClientConfig.copy (
    endPoint = sparqlEndPoint,
    prefixMapping = Option(pm)
  )

  protected lazy val actorSparqlClient = SparqlClientFactory(context, sparqlClientConfig)
}

