package com.modelfabric.sparql.spray.client

import com.modelfabric.sparql.api.{SparqlUpdate, SparqlStatement, SparqlQuery}

import scala.language.postfixOps
import akka.actor.{ ActorLogging, Actor, ActorRef }
import spray.http.HttpResponse
import com.modelfabric.akka.actor.UnknownMessageHandler

/**
 * Messages
 */
abstract class MessageSparqlClient

case class MessageSparqlClientQuerySolution(sparql : SparqlQuery, qs : QuerySolution) extends MessageSparqlClient

case class MessageSparqlClientNoQuerySolutions(sparql : SparqlQuery) extends MessageSparqlClient

case class MessageSparqlClientQueryEnd(sparql : SparqlQuery, rs : ResultSet) extends MessageSparqlClient

case class MessageSparqlClientUpdateSuccessful(sparql : SparqlUpdate) extends MessageSparqlClient

case class MessageSparqlStatementFailed(
  statement : SparqlStatement,
  response : Option[HttpResponse],
  error : Option[Throwable]) extends MessageSparqlClient

/**
 * A SparqlClient is an Actor that sends SPARQL statements to a given endPoint and send you a Message for every
 * returned result row (MessageSparqlClientQuerySolution). It also sends a message to you when all query solutions
 * have been processed.
 *
 * @param config A SparqlClientConfig
 */
abstract class SparqlClient(
  val config : SparqlClientConfig
)
  extends UnknownMessageHandler
{

  def execute(requestor_ : ActorRef, sparql_ : SparqlStatement)

  def localReceive : Receive = {
    case sparql_ : SparqlStatement ⇒ execute(sender, sparql_)
  }

  def receive = localReceive orElse unknownMessage
}

