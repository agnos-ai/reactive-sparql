package com.modelfabric.sparql.stream.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, Framing, GraphDSL, Source, ZipWith}
import akka.util.ByteString
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.SparqlClientConstants.{modelFactory => mf, valueFactory => vf}
import org.eclipse.rdf4j.model.{IRI, Model, Resource}
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import scala.concurrent.ExecutionContext
import scala.util.{Success, Try}

object SparqlConstructToModelFlowBuilder {
  val `rdf:subject`  : IRI = vf.createIRI(NamespaceConstants.RDF, "subject")
  val `rdf:predicate`: IRI = vf.createIRI(NamespaceConstants.RDF, "predicate")
  val `rdf:object`   : IRI = vf.createIRI(NamespaceConstants.RDF, "object")
  val `rdf:graph`    : IRI = vf.createIRI(NamespaceConstants.RDF, "graph")
}


trait SparqlConstructToModelFlowBuilder extends SparqlClientHelpers {

  import SparqlConstructToModelFlowBuilder._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val dispatcher: ExecutionContext

  type Sparql = String

  def sparqlModelConstructFlow(endpointFlow: HttpEndpointFlow[SparqlRequest]): Flow[SparqlRequest, SparqlResponse, NotUsed] = {

    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val converter = builder.add(Flow.fromFunction(sparqlToRequest(endpointFlow.endpoint)).async.named("mapping.sparqlToConstruct"))
      val broadcastConstructHttpResponse = builder.add(Broadcast[(Try[HttpResponse], SparqlRequest)](2).async.named("broadcast.constructResponse"))
      val resultMaker = builder.add(Flow.fromFunction(responseToSparqlResponse).async.named("mapping.makeResponseFromHeader"))
      val resultZipper = builder.add(ZipWith[SparqlResult, SparqlResponse, SparqlResponse]((result, response) =>
        response.copy(
          result = List(result)
        )
      ).async.named("zipper.updateResultZipper"))

      converter ~> endpointFlow.flow ~> broadcastConstructHttpResponse

      broadcastConstructHttpResponse ~> responseToModelFlow ~> resultZipper.in0
      broadcastConstructHttpResponse ~> resultMaker         ~> resultZipper.in1

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
    * This flow will consume the Http response entity and produces a corresponding SparqlResult
    */
  val responseToModelFlow: Flow[(Try[HttpResponse], SparqlRequest), SparqlResult, NotUsed] = {
    Flow[(Try[HttpResponse], SparqlRequest)]
      .flatMapConcat {
        // TODO: there are issues here with Stardog...
        case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _) if entity.isChunked() =>
          entity.withoutSizeLimit().dataBytes/*.scan(ByteString.empty)(_ ++ _)*/.zip(Source.single(entity.contentType))
        case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _) =>
          entity.withoutSizeLimit().dataBytes.zip(Source.single(entity.contentType))
      }
/*
      .flatMapConcat {
        case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), _) =>
          val fut = entity.withoutSizeLimit().dataBytes.runFold(ByteString.empty)(_ ++ _).map( (_, entity.contentType) )
          Source.fromFuture(fut)
      }
*/
      .map { x =>
        Rio.parse(x._1.iterator.asInputStream, "", mapContentTypeToRdfFormat(x._2))
      }
      .flatMapConcat( m => Source.single[Model](m).via(deReifyConstructSubGraph()))
      .map(SparqlModelResult)
  }

  //@deprecated
  val responseToPagingModelFlow: Flow[(Try[HttpResponse], SparqlRequest), SparqlResult, NotUsed] = {
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
