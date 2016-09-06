package com.modelfabric.sparql.api

import scala.util.{Failure, Try}

sealed trait SparqlClientProtocol

/**
  * Represents a single request to be sent to the triple store.
  */
trait Request[T] extends SparqlClientProtocol

/**
  * "Is-Alive" request (aka Ping)
  */
// TODO: implement functionality around this and provide tests as well
case object IsAliveRequest extends Request[Boolean]

/**
  * Represents a response from the triple store.
  *
  * @param request the request is returned with the response to enable matching
  *                these up in case they get out of order
  * @param result the result of the operation
  */
case class Response[T](
  request: Request[T],
  result: Try[T] = Failure[T](new IllegalArgumentException)
) extends SparqlClientProtocol {
  lazy val success: Boolean = result.isSuccess
}
