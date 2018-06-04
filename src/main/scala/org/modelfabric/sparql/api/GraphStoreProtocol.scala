package org.modelfabric.sparql.api

import java.net.URL
import java.nio.file.Path

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods._
import org.eclipse.rdf4j.model.{IRI, Model}
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.RDFFormat.NTRIPLES


/**
  * Implementation of the graph-store API messages.
  */
trait GraphStoreProtocol extends ClientAPIProtocol

abstract class GraphStoreRequest extends ClientHttpRequest with GraphStoreProtocol {
  def context: Option[Any]
}

/**
  * The response entity for graph store operations.
  * @param request    the request object
  * @param success    true if the operation was successful
  * @param statusCode the HTTP status code of the response
  * @param statusText the HTTP status reason text
  * @param model      the response RDF graph (model) if available
  */
case class GraphStoreResponse
(
  request: GraphStoreRequest,
  success: Boolean,
  statusCode: Int,
  statusText: String,
  model: Option[Model] = None
) extends ClientHttpResponse with GraphStoreProtocol

object GetGraphM {
  def unapply(arg: GetGraph): Option[(Option[IRI], HttpMethod)] = {
    Some((arg.graphIri, arg.httpMethod))
  }
}

/**
  * Represents the operation to retrieve a single graph via the graph-store protocol.
  * @param graphIri the graph IRI to get, if not specified this denotes the DEFAULT graph
  *
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-get]]
  */
case class GetGraph(graphIri: Option[IRI], context: Option[Any] = None) extends GraphStoreRequest {
  final override val httpMethod: HttpMethod = GET
}


object DropGraphM {
  def unapply(arg: DropGraph): Option[(Option[IRI], HttpMethod)] = {
    Some((arg.graphIri, arg.httpMethod))
  }
}

/**
  * Represents an operation to drop a single graph via the graph-store protocol.
  * @param graphIri the graph IRI to drop, if not specified this denotes the DEFAULT graph
  *
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-delete]]
  */
case class DropGraph(graphIri: Option[IRI], context: Option[Any] = None) extends GraphStoreRequest {
  final override val httpMethod: HttpMethod = DELETE
}


/**
  * Represents an operation to insert graph(s) via the graph-store protocol.
  * @param graphIri    an optional graph IRI specifier, will be added as the value of the "?graph=uri"
  *                    query parameter
  * @param mergeGraphs merge if true, replace graph if false
  *
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-post]]
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-put]]

  */
abstract class InsertGraph(val graphIri: Option[IRI], val mergeGraphs: Boolean)
  extends GraphStoreRequest {

  override val httpMethod: HttpMethod = if (mergeGraphs) POST else PUT

  val modelFormat: RDFFormat
}

object InsertGraphFromModelM {

   def unapply(arg: InsertGraphFromModel): Option[(Model, RDFFormat, Option[IRI], HttpMethod)] = {
    Some((arg.graphModel, arg.modelFormat, arg.graphIri, arg.httpMethod))
  }
}

/**
  * Used to insert an in-memory RDF model into the triple store.
  * @param graphModel  the graph model
  * @param graphIri    optional graph IRI, defaults to None
  * @param mergeGraphs indicates if the inserted data should be merged data already in the specified graph in
  *                    the database, if set to false the previously stored data in the graph will be replaced
  *                    with the payload, defaults to false
  * @param context     an optional context to be carried along the flow
  */
case class InsertGraphFromModel
(
  graphModel: Model,
  override val graphIri: Option[IRI] = None,
  override val mergeGraphs: Boolean = false,
  context: Option[Any] = None
) extends InsertGraph(graphIri, mergeGraphs) {

  /**
    * We only use NTRIPLES upload format when uploading in-memory Models.
    */
  final override val modelFormat: RDFFormat = NTRIPLES

}


object InsertGraphFromURLM {
  def unapply(arg: InsertGraphFromURL): Option[(URL, RDFFormat, Option[IRI], HttpMethod)] = {
    Some((arg.url, arg.modelFormat, arg.graphIri, arg.httpMethod))
  }
}

/**
  * Used to insert RDF contents from a file or another resource into the triple store.
  * @param url         the URL of the file to use
  * @param modelFormat the RDF format to use, supporting all formats available in org.eclipse.rdf4j.rio.Rio
  * @param graphIri    an optional graph IRI, will insert into the default graph if no graph is specified.
  *                    NB: if the incoming RDF is in "quads" format, i.e. NQUADS
  *                    or JSON-LD, the graph
  *                    in the RDF is ignored and instead the graphIri property is used.
  * @param mergeGraphs indicates if the inserted data should be merged data already in the specified graph in
  *                    the database, if set to false the previously stored data in the graph will be replaced
  *                    with the payload, defaults to false
  * @param context     an optional context to be carried along the flow
  */
case class InsertGraphFromURL
(
  url: URL,
  modelFormat: RDFFormat,
  override val graphIri: Option[IRI] = None,
  override val mergeGraphs: Boolean = false,
  context: Option[Any] = None
) extends InsertGraph(graphIri, mergeGraphs)

object InsertGraphFromPathM {
  def unapply(arg: InsertGraphFromPath): Option[(Path, RDFFormat, Option[IRI], HttpMethod)] = {
    Some((arg.path, arg.modelFormat, arg.graphIri, arg.httpMethod))
  }
}

/**
  * Used to insert RDF contents from a file or another resource into the triple store.
  * @param path        the local filesystem path of the file to use
  * @param modelFormat the RDF format to use, supporting all formats available in org.eclipse.rdf4j.rio.Rio
  * @param graphIri    an optional graph IRI, will insert into the default graph if no graph is specified.
  *                    NB: if the incoming RDF is in "quads" format, i.e. NQUADS
  *                    or JSON-LD, the graph
  *                    in the RDF is ignored and instead the graphIri property is used.
  * @param mergeGraphs indicates if the inserted data should be merged data already in the specified graph in
  *                    the database, if set to false the previously stored data in the graph will be replaced
  *                    with the payload, defaults to false
  * @param context     an optional context to be carried along the flow
  */
case class InsertGraphFromPath
(
  path: Path,
  modelFormat: RDFFormat,
  override val graphIri: Option[IRI] = None,
  override val mergeGraphs: Boolean = false,
  context: Option[Any] = None
) extends InsertGraph(graphIri, mergeGraphs)
