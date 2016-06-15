package com.modelfabric.client.sparql.spray.client

import akka.actor.{Actor, ActorLogging}
import com.modelfabric.client.sparql.spray.PrefixMapping
import com.modelfabric.client.sparql.spray.query.QueryToTestConnection
import akka.actor.Actor
import akka.actor.Props
import scala.concurrent.duration._

case object Launch

/**
 * This Actor sends a test SPARQL statements as a kind of heart beat to the specified SPARQL endpoint.
 */
class SparqlConnectionTester extends Actor with ActorLogging with SparqlClientHelper {

  import context.dispatcher

  implicit protected val pm = PrefixMapping.standard

  val cancellable = context.system.scheduler.schedule(
    1030 seconds,
    1030 seconds,
    self,
    Launch
  )

  override def postStop() {

    cancellable.cancel()
  }

  def receive = {
    case Launch => launch()
  }

  def launch() {
    log.info("Launching the SPARQL Connection Tester")

    actorSparqlClient ! QueryToTestConnection()
  }

}
