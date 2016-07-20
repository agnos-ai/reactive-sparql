package com.modelfabric.sparql.spray.client

import akka.actor.{ActorRefFactory, ActorSystem}
import com.typesafe.config.{ConfigFactory, Config}
import spray.util._

case class SparqlSettings(
  sparqlEndPoint : Option[String],                            // application.conf -> sparql.client-endpoint
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

    val endPoint = Some(c getString "endpoint")
    val userId = Some(c getString "userId")
    val password = Some(c getString "password")

    SparqlSettings(
      endPoint,
      userId,
      password
    )
  }

  def apply(optionalSettings: Option[SparqlSettings])(implicit actorRefFactory: ActorRefFactory): SparqlSettings =
    optionalSettings getOrElse apply(actorSystem)
}
