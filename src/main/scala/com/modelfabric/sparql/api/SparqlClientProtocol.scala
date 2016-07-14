package com.modelfabric.sparql.api

sealed trait SparqlClientProtocol

/**
  * Request
  */
trait Request extends SparqlClientProtocol

/**
  * Represents a single Sparql request to be sent to the triple store.
  *
  * @param statement the sparql statement string, any | margins will be stripped automatically
  * @param context the context object the request carries with it, can be anything, but most
  *                likely it will be an identity object or an actor reference of the caller
  *                to enable proper asynchronous processing
  */
case class SparqlRequest(statement: SparqlStatement) extends Request

/**
  * "Is-Alive" request
  */
case object PingRequest extends Request

/**
  * Response
  */
trait Response extends SparqlClientProtocol

/**
  * Represents a response from the triple store for a Sparql request.
  *
  * @param request the underlying request object is returned with the response
  * @param success true if the sparql statement execution succeeded
  * @param resultSet
  * @param error
  */
case class SparqlResponse(
  request: SparqlRequest,
  success: Boolean = true,
  resultSet: Option[ResultSet] = None,
  error: Option[SparqlClientError] = None) extends Response

/**
  * "Is-Alive" response
  *
  * @param success
  */
case class PingResponse(success: Boolean = true) extends Response


/**
  * Error messages
  */
sealed trait SparqlClientError
case class SparqlClientRequestFailed(message: String) extends RuntimeException(message) with SparqlClientError
case class SparqlClientRequestFailedWithError(message: String, throwable: Throwable) extends RuntimeException(message, throwable) with SparqlClientError
