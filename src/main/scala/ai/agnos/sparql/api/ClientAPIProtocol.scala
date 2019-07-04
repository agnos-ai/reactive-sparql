package ai.agnos.sparql.api

import akka.http.scaladsl.model.HttpMethod


/**
  * Client API Protocol, all concrete API requests use this
  */
trait ClientAPIProtocol

/**
  * Request
  */
trait Request extends ClientAPIProtocol

/**
  * Response
  */
trait Response extends ClientAPIProtocol

/**
  * Represents any request sent to the Sparql endpoint via HTTP, which may
  * include Sparql statements or graph-store protocol operations.
  */
abstract class ClientHttpRequest extends Request {

  /**
    * @return the HTTP Method to be used to communicate with the endpoint
    */
  def httpMethod: HttpMethod

}


abstract class ClientHttpResponse extends Response {

  /**
    * Indicates if the request has been successful
    * @return
    */
  def success: Boolean

}
