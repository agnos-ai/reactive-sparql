package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.modelfabric.sparql.util.HttpEndpoint
import com.typesafe.sslconfig.akka.AkkaSSLConfig

import scala.util.Try


trait HttpClientFlowBuilder {

  /**
    * Returns the default
    * @param endpoint
    * @tparam T
    * @return
    */
  def pooledClientFlow[T](endpoint: HttpEndpoint)
                         (implicit system: ActorSystem, materializer: ActorMaterializer)
  : Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    endpoint.protocol match {
      case "http" => pooledHttpClientFlow(endpoint)
      case "https" => pooledHttpsClientFlow(endpoint, defaultHttpsConnectionContext)
      case protocol => throw new IllegalArgumentException(s"invalid protocol specified: ${protocol}")
    }
  }

  /**
    * The unsecured (HTTPS) client Http flow.
    * @param endpoint
    * @tparam T
    * @return
    */
  def pooledHttpClientFlow[T](endpoint: HttpEndpoint)
                             (implicit system: ActorSystem, materializer: ActorMaterializer)
  : Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    if(endpoint.protocol == "https") { throw new IllegalArgumentException(s"expected http for endpoint $endpoint")}
    Flow[(HttpRequest, T)]
      .log("beforeHttpRequest", req => req._1.httpMessage)
      .via(Http().cachedHostConnectionPool[T](endpoint.host, endpoint.port))
      .log("afterHttpRequest")
  }

  /**
    * The secured (HTTPS) client Http flow.
    * @param endpoint
    * @param context defaults to {{#defaultHttpsConnectionContext}}
    * @tparam T
    * @return
    */
  def pooledHttpsClientFlow[T](endpoint: HttpEndpoint, context: HttpsConnectionContext)
                              (implicit system: ActorSystem, materializer: ActorMaterializer)
  : Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    if(endpoint.protocol == "http") { throw new IllegalArgumentException(s"expected https for endpoint $endpoint")}
    Flow[(HttpRequest, T)]
      .log("beforeHttpsRequest", req => req._1.httpMessage)
      .via(Http().cachedHostConnectionPoolHttps[T](endpoint.host, endpoint.port, context))
      .log("afterHttpsRequest")
  }

  /**
    * The default HTTPS connection context is very slack, use only for tests - not very useful for real world use.
    */
  protected def defaultHttpsConnectionContext()
                                             (implicit system: ActorSystem, materializer: ActorMaterializer)
  : HttpsConnectionContext = {
    val badSslConfig = AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose.withDisableSNI(true)))
    Http().createClientHttpsContext(badSslConfig)
  }

}
