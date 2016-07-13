package com.modelfabric.sparql.api

sealed trait SparqlClientProtocol

/**
  * Request
  */
trait Request extends SparqlClientProtocol

/**
  * Represents a single Sparql request to be sent to the triple store.
  * @param statement
  */
case class SparqlRequest(statement: SparqlStatement) extends Request

/**
  * "Is-Alive" request
  */
// JC: not used
case object PingRequest extends Request

/**
  * Response
  */
trait Response extends SparqlClientProtocol

/**
  * Represents a response from the triple store for a Sparql request.
  *
  * @param success
  * @param resultSet
  * @param error
  */
case class SparqlResponse(
  success: Boolean = true,
  resultSet: Option[ResultSet] = None,
  error: Option[SparqlClientError] = None) extends Response

/**
  * "Is-Alive" response
  * @param success
  */
// JC: not used
case class PingResponse(success: Boolean = true) extends Response


/**
  * Error messages
  */
sealed trait SparqlClientError
case class SparqlClientRequestFailed(message: String) extends RuntimeException(message) with SparqlClientError
case class SparqlClientRequestFailedWithError(message: String, throwable: Throwable) extends RuntimeException(message, throwable) with SparqlClientError
