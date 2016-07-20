package com.modelfabric.sparql.spray.client

import akka.actor.ActorSystem
import com.modelfabric.sparql.api.PrefixMapping

/**
 * SPARQL Client Config
 */
case class SparqlClientConfig(
  userId : Option[String] = Some("admin"),
  password : Option[String] = Some("admin"),
  endPoint : Option[String] = None,
  retryCount : Int = 2,
  prefixMapping : Option[PrefixMapping] = None
)

object SparqlClientConfig {

  def apply(system: ActorSystem): SparqlClientConfig = apply(SparqlSettings(system))

  def apply(settings: SparqlSettings) : SparqlClientConfig = SparqlClientConfig().copy(
    endPoint = settings.sparqlEndPoint,
    userId = settings.userId,
    password = settings.password
  )
}
