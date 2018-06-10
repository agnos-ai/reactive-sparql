package org.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, Framing, GraphDSL, Merge, Partition, Source, ZipWith}
import akka.util.ByteString
import org.modelfabric.sparql.api._
import org.modelfabric.sparql.stream.client.SparqlClientConstants.{modelFactory => mf, valueFactory => vf}
import org.eclipse.rdf4j.model.{IRI, Model, Resource}
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object SparqlConstructFlowBuilder {
  val `rdf:subject`  : IRI = vf.createIRI(NamespaceConstants.RDF, "subject")
  val `rdf:predicate`: IRI = vf.createIRI(NamespaceConstants.RDF, "predicate")
  val `rdf:object`   : IRI = vf.createIRI(NamespaceConstants.RDF, "object")
  val `rdf:graph`    : IRI = vf.createIRI(NamespaceConstants.RDF, "graph")
}


trait SparqlConstructFlowBuilder extends SparqlClientHelpers with ErrorHandlerSupport {

  import SparqlConstructFlowBuilder._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val dispatcher: ExecutionContext

  type Sparql = String

  def sparqlConstructFlow(endpointFlow: HttpEndpointFlow[SparqlRequest]): Flow[SparqlRequest, SparqlResponse, NotUsed] = {

    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpointFlow.endpoint)).named("mapping.sparqlToConstruct"))
      val broadcastConstructHttpResponse = builder.add(Broadcast[(Try[HttpResponse], SparqlRequest)](2).named("broadcast.constructResponse"))
      val resultMaker = builder.add(Flow.fromFunction(responseToSparqlResponse).named("mapping.makeResponseFromHeader"))
      val resultZipper = builder.add(ZipWith[SparqlResult, SparqlResponse, SparqlResponse]((result, response) =>
        response.copy(
          result = List(result)
        )
      ).async.named("zipper.updateResultZipper"))

      converter ~> endpointFlow.flow ~> broadcastConstructHttpResponse

      broadcastConstructHttpResponse ~> responseToResultFlow ~> resultZipper.in0
      broadcastConstructHttpResponse ~> resultMaker          ~> resultZipper.in1

      FlowShape(converter.in, resultZipper.out)
    } named "flow.sparqlUpdateRequest")

  }

  def deReifyConstructSubGraph(): Flow[Model, Model, NotUsed] = {
    import scala.collection.JavaConverters._
    Flow[Model]
      .mapConcat(_.stream().iterator().asScala.toList)
      .sliding(4,4)
      .fold(mf.createEmptyModel()) {
        case (model, s) =>
          model.add(
            s.find(_.getPredicate == `rdf:subject`).get.getObject.asInstanceOf[Resource],
            s.find(_.getPredicate == `rdf:predicate`).get.getObject.asInstanceOf[IRI],
            s.find(_.getPredicate == `rdf:object`).get.getObject,
            s.find(_.getPredicate == `rdf:graph`).get.getObject.asInstanceOf[Resource])
          model
      }
  }

  /**
    * This flow will consume the Http response entity and produces a corresponding SparqlModelResult
    */
  lazy val responseToSuccessFlow: Flow[(Try[HttpResponse], SparqlRequest), SparqlResult, NotUsed] = {
    Flow[(Try[HttpResponse], SparqlRequest)]
      .flatMapConcat {
        // TODO: Add support for sliding through the entity 4 lines at a time (see responseToPagingModelFlow)
        case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _) =>
          entity.withoutSizeLimit().dataBytes.fold(ByteString.empty)(_ ++ _).zip(Source.single(entity.contentType))
      }
      .map { x =>
        Rio.parse(x._1.iterator.asInputStream, "", mapContentTypeToRdfFormat(x._2))
      }
      .flatMapConcat( m => Source.single[Model](m).via(deReifyConstructSubGraph()))
      .map(SparqlModelResult)
  }

  /**
    * This flow will also consume the Http response entity if it is received via a valid HTTP response with
    * an HTTP status code and produces a corresponding SparqlErrorResult
    */
  lazy val responseToFailureFlow: Flow[(Try[HttpResponse], SparqlRequest), SparqlResult, NotUsed] = {
    Flow[(Try[HttpResponse], SparqlRequest)]
      .flatMapConcat {
        case (Success(HttpResponse(code, _, entity, _)), _) =>
          entity.withoutSizeLimit().dataBytes.fold(ByteString.empty)(_ ++ _).map {
            case message: ByteString => SparqlErrorResult(
              error = new RuntimeException(
                s"${message.utf8String}"
              ),
              code = code.intValue(),
              message = "SPARQL endpoint returned unexpected response body")
          }
        case (Failure(err), req) =>
          errorHandler.handleError(err)
          Source.single(SparqlErrorResult(err, 0, s"unexpected error when processing flow for request: ${req}"))
      }
  }

  val responseToResultFlow: Flow[(Try[HttpResponse], SparqlRequest), SparqlResult, NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val switch = builder.add(Partition[(Try[HttpResponse], SparqlRequest)](2, {
        case (Success(HttpResponse(StatusCodes.OK, _, _, _)), _) => 0
        case _ => 1
      }))

      val merge = builder.add(Merge[SparqlResult](2))

      switch.out(0) ~> responseToSuccessFlow ~> merge.in(0)
      switch.out(1) ~> responseToFailureFlow ~> merge.in(1)

      FlowShape(switch.in, merge.out)
    })
  }


  @deprecated
  lazy val responseToPagingModelFlow: Flow[(Try[HttpResponse], SparqlRequest), SparqlResult, NotUsed] = {
    Flow[(Try[HttpResponse], SparqlRequest)]
      .flatMapConcat {
        case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _) => entity.withoutSizeLimit().getDataBytes()
      }
      .via(Framing.delimiter(ByteString.fromString("\n"), 1*1024, allowTruncation = true))
      .map( bs => Rio.parse(bs.iterator.asInputStream, "", RDFFormat.NQUADS).stream().findFirst().get())
      .sliding(4, 4)
      .map { s =>
        //NOTE: we are assuming here that the order of the triples representing the parts of the ModelStatement is
        //      always correct, i.e. 1. Subject 2. Predicate 3. Object 4. Graph. We are only interested in the Object
        //      since this is the one that carries the value.
        val out = mf.createEmptyModel()
        out.add(
          s(0).getObject.asInstanceOf[Resource],
          s(1).getObject.asInstanceOf[IRI],
          s(2).getObject,
          s(3).getObject.asInstanceOf[Resource])
        SparqlModelResult(out)
      }
  }

}
