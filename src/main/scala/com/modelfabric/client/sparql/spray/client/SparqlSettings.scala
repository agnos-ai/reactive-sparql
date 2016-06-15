package com.modelfabric.client.sparql.spray.client

import akka.actor.{ActorRefFactory, ActorSystem}
import com.typesafe.config.{ConfigFactory, Config}
import spray.util._
import SparqlClientType.SparqlClientType

case class SparqlSettings(
  sparqlEndPoint : Option[String],                            // application.conf -> sparql.client-endpoint
  clientType: SparqlClientType = SparqlClientType.HttpSpray,  // application.conf -> sparql.client-type
  userId: Option[String],                                     // application.conf -> sparql.client-userId
  password: Option[String]                                    // application.conf -> sparql.client-password
) {

  require(sparqlEndPoint.nonEmpty)
}

/**
 * Create an instance of SparqlSettings from the sparql.client{} section in application.conf.
 *
 * Inspired by the Spray.io class ClientConnectionSettings
 */
object SparqlSettings {

  def apply(system: ActorSystem): SparqlSettings =
    apply(system.settings.config getConfig "sparql.client")

  def apply(config: Config): SparqlSettings = {

    val c = config.withFallback(ConfigFactory.defaultReference(getClass.getClassLoader))

    val sparqlClientType = SparqlClientType.values.find(_.toString == (c getString "type"))
    val endPoint = Some(c getString "endpoint")
    val userId = Some(c getString "userId")
    val password = Some(c getString "password")

    SparqlSettings(
      endPoint,
      sparqlClientType.getOrElse(SparqlClientType.HttpSpray),
      userId,
      password
    )
  }

  def apply(optionalSettings: Option[SparqlSettings])(implicit actorRefFactory: ActorRefFactory): SparqlSettings =
    optionalSettings getOrElse apply(actorSystem)
}
