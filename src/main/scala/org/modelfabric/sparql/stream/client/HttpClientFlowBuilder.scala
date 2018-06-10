package org.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult }
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.modelfabric.sparql.util.HttpEndpoint
import com.typesafe.sslconfig.akka.AkkaSSLConfig

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}


trait HttpClientFlowBuilder {

  def defaultHttpClientFlow[T](endpoint: HttpEndpoint)
                              (implicit system: ActorSystem, materializer: ActorMaterializer)
  : Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    queuedAndPooledHttpClientFlow[T](endpoint, queueSize = 10, overflowStrategy = OverflowStrategy.backpressure)
  }

  /**
    * Returns the default
    * @param endpoint
    * @tparam T
    * @return
    */
  def pooledHttpClientFlow[T](endpoint: HttpEndpoint, akkaSSLConfig: Option[AkkaSSLConfig] = None)
                             (implicit system: ActorSystem, materializer: ActorMaterializer)
  : Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    endpoint.protocol match {
      case "http" => Flow[(HttpRequest, T)].via(Http().cachedHostConnectionPool(endpoint.host, endpoint.port))
      case "https" => Flow[(HttpRequest, T)].via(Http().cachedHostConnectionPoolHttps(endpoint.host, endpoint.port, httpsConnectionContext(akkaSSLConfig)))
      case protocol => throw new IllegalArgumentException(s"invalid protocol specified: ${protocol}")
    }
  }

  /**
    * Creates a flow that allows for access to the connection pool via a bounded request queue.
    *
    * {@see https://doc.akka.io/docs/akka-http/current/client-side/host-level.html#using-the-host-level-api-with-a-queue}
    *
    * @param endpoint the HTTP(S) endpoint
    * @param queueSize the size of the queue
    * @param overflowStrategy the overflow strategy to apply if the queue overruns
    * @param akkaSSLConfig SSL config
    * @param system
    * @param materializer
    * @tparam T
    * @return
    */
  def queuedAndPooledHttpClientFlow[T]
  (
    endpoint: HttpEndpoint,
    queueSize: Int = 10,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    akkaSSLConfig: Option[AkkaSSLConfig] = None
  )
  (
    implicit system: ActorSystem, materializer: ActorMaterializer
  ): Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {

    implicit val _ec = system.dispatcher

    // Materialize a queue with the desired buffer and overflow strategy
    val queue = Source.queue[(HttpRequest, Promise[HttpResponse])](queueSize, overflowStrategy)
      .via(pooledHttpClientFlow[Promise[HttpResponse]](endpoint, akkaSSLConfig))
      .toMat(Sink.foreach({
        case ((Success(resp), p)) => p.success(resp)
        case ((Failure(e), p)) => p.failure(e)
      }))(Keep.left)
      .run()

    // queue a request onto the buffered connection pool stream
    def queueRequest(request: HttpRequest): Future[HttpResponse] = {
      val responsePromise = Promise[HttpResponse]()
      queue.offer(request -> responsePromise).flatMap {
        case QueueOfferResult.Enqueued    => responsePromise.future
        case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("HTTP connection pool Queue has overflown. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("HTTP connection pool wwas closed (pool shut down) while running the request. Try again later."))
      }
    }

    // Make a flow that routes requests along the connection pool and maintains all possible error scenarios, including
    // 1) HTTP request failures
    // 2) queue overflowStrategy
    val flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] =
      Flow[(HttpRequest, T)]
        .mapAsync(1) {
          case (req, ctx) =>
            // a trick to maintain the inner future's value, so it can be unwrapped via mapAsync above
            // which will give a meaningful error about what has failed to the user
            queueRequest(req).transform {
              case s@Success(_) => Success((s, ctx))
              case f@Failure(_) => Success((f, ctx))
            }
        }
    flow
  }

  /**
    * Override if the default connection context is not satisfactory.
    *
    * @return
    */
  protected def httpsConnectionContext
  (
    config: Option[AkkaSSLConfig]
  )
  (implicit system: ActorSystem, materializer: ActorMaterializer): HttpsConnectionContext = {
    Http().createClientHttpsContext(config.getOrElse(defaultSSLConfig))
  }

  /**
    * The default HTTPS connection context config is very slack, use only for tests - not very useful for real world use.
    */
  protected def defaultSSLConfig()
                                (implicit system: ActorSystem, materializer: ActorMaterializer)
  : AkkaSSLConfig = {
    AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose.withDisableSNI(true)))
  }

}
