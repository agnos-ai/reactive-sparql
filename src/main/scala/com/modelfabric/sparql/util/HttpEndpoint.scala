package com.modelfabric.sparql.util

import java.net.ServerSocket

/**
  * HttpEndpoint construction factory
  */
object HttpEndpoint {

  /**
    * Get an HTTP endpoint bound to 'localhost' where a free port is automatically assigned
    * and can be used to be bound to.
    *
    * The generated endpoint will be '''http://localhost:${assigned-port}${path}'''
    *
    * @param path the desired path part for the endpoint
    * @return
    */
  def localhostWithAutomaticPort(path: String): HttpEndpoint =
    new HttpEndpointBuilder(assignPortAutomatically = true, useResourcePath = path).endpoint

  /**
    * Construct an endpoint from a string URL representation,
    * which corresponds to '''[${protocol}://]${host}[:${port}][${path}]'''
    * Protocol, port and path are optional. Default protocol is http://,
    * default port is 80, the default path is empty.
    *
    * @param endpointString
    * @return
    */
  def apply(endpointString: String): HttpEndpoint =
    new HttpEndpointBuilder(Some(endpointString), false).endpoint

  /**
    * Construct an endpoint from individual parts.
    * @param protocol
    * @param host
    * @param port
    * @param path
    * @return
    */
  def apply(protocol: String, host: String, port: Integer, path: String): HttpEndpoint =
    new HttpEndpoint(protocol, host, port, path)

  /**
    * Construct an HTTP endpoint from individual parts.
    *
    * @param host
    * @param port
    * @return
    */
  def apply(host: String, port: Integer): HttpEndpoint =
    new HttpEndpoint(defaultProtocol, host, port, "")

  /**
    * Construct an endpoint from individual parts with the desired path.
    *
    * @param host
    * @param port
    * @param path
    * @return
    */
  def apply(host: String, port: Integer, path: String): HttpEndpoint =
    new HttpEndpoint(defaultProtocol, host, port, path)

  private val defaultProtocol: String = "http"
  private val defaultHost: String = "localhost"

  private sealed class HttpEndpointBuilder(
    environmentEndpoint: Option[String] = None,
    assignPortAutomatically: Boolean, useResourcePath: String = "") {

    private val EndpointRegex = "(http[s]{0,1}://)?([a-zA-Z\\-\\.0-9]+)(:\\d{1,6})?(/.+)?".r

    val protocol = environmentEndpoint match {
      case Some(EndpointRegex(protocol, _, _, _)) if protocol != null => protocol.replace("://", "")
      case _ => defaultProtocol
    }
    private val defaultPort: Int = protocol match {
      case "http" => 80
      case "https" => 443
    }

    val host = environmentEndpoint match {
      case Some(EndpointRegex(_, host, _, _)) if host != null => host
      case _ => defaultHost
    }

    val port: Int = environmentEndpoint match {
      case Some(EndpointRegex(_, _, port, _)) if port != null => port.replace(":","").toInt
      case Some(EndpointRegex(_, _, null, _))  => defaultPort
      case _ if assignPortAutomatically =>
        val socket = new ServerSocket(0)
        val p = socket.getLocalPort
        socket.close()
        p
      case _ => defaultPort
    }

    val path: String = environmentEndpoint match {
      case Some(EndpointRegex(_, _, _, path)) if path != null => path
      case _ => useResourcePath
    }

    val endpoint = HttpEndpoint(protocol, host, port, path)
  }
}

/**
  * Internal representation of an HttpEndpoint.
  *
  * @param protocol
  * @param host
  * @param port
  * @param path
  */
case class HttpEndpoint(protocol: String, host: String, port: Int, path: String) {
  /**
    * Shows the desired fully qualified URL of the endpoint built from the individual components
    */
  val url: String = s"$protocol://$host${
    port match {
      case 80 => ""
      case 443 => ""
      case x => s":$x"
    }
  }$path"

}

