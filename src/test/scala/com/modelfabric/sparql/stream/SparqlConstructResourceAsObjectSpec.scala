package com.modelfabric.sparql.stream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.modelfabric.sparql.api._
import org.scalatest.DoNotDiscover

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

@DoNotDiscover
class SparqlConstructResourceAsObjectSpec
  extends TestKit(ActorSystem("SparqlConstructResourceAsObjectSpec"))
  with SparqlConstructSpecBase {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

  "The SparqlConstructResourceAsObject" must {

    "1. Clear the data" in {
      clearData()
    }

    "2 Allow one insert" in {

      sink.request(1)
      source.sendNext(SparqlRequest(insertModelGraphDataWithObjResource))
      assertSuccessResponse(sink.expectNext(receiveTimeout))
    }

    "3. Get the filtered graph just inserted via a model construct query" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstructResourceAsObject(
            resourceIRIs = "urn:test:mfab:type:0" :: Nil
          )
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 1)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }
  }

}
