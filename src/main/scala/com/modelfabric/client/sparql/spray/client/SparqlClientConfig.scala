package com.modelfabric.client.sparql.spray.client

import SparqlClientType._
import akka.actor.ActorSystem
import com.modelfabric.client.sparql.spray.PrefixMapping
import com.typesafe.config.Config

/**
 * SPARQL Client Config
 */
case class SparqlClientConfig(
  clientType : SparqlClientType = SparqlClientType.HttpSpray,
  userId : Option[String] = Some("admin"),
  password : Option[String] = Some("admin"),
  endPoint : Option[String] = None,
  retryCount : Int = 2,
  prefixMapping : Option[PrefixMapping] = None
)

object SparqlClientConfig {

  def apply(system: ActorSystem): SparqlClientConfig = apply(SparqlSettings(system))

  def apply(settings: SparqlSettings) : SparqlClientConfig = SparqlClientConfig().copy(
    clientType = settings.clientType,
    endPoint = settings.sparqlEndPoint,
    userId = settings.userId,
    password = settings.password
  )
}
