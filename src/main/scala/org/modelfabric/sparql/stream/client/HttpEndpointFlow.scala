package org.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import org.modelfabric.sparql.util.HttpEndpoint

import scala.util.Try

/**
  * This class represents an encapsulation of an Http endpoint with it's corresponding flow. The flow can be used
  * to send HttpRequests and receive HttpResponses on the other end from the associated endpoint. The T type parameter
  * determines the type of the companion object that is carried throughout the flow together with the request
  * to match up requests with responses in cases these may arrive out of order.
  * @param endpoint the http endpoint
  * @param flowMaker a function that can make a flow out of the endpoint
  * @tparam T the type of the companion object
  */
case class HttpEndpointFlow[T]
(
  endpoint: HttpEndpoint,
  flowMaker: (HttpEndpoint => Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed])
) {
  lazy val flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = flowMaker(endpoint)
}
