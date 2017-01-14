package com.modelfabric.sparql.api

import java.net.{URI, URL}
import java.nio.file.Path

import com.modelfabric.sparql.api.HttpMethod.{DELETE, GET, POST, PUT}
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.RDFFormat.{JSONLD, NQUADS, NTRIPLES}


/**
  * Implementation of the graph-store API messages.
  */
trait GraphStoreProtocol extends ClientAPIProtocol

abstract class GraphStoreRequest extends ClientHttpRequest with GraphStoreProtocol

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
  def unapply(arg: GetGraph): Option[(Option[URI], HttpMethod)] = {
    Some((arg.graphUri, arg.httpMethod))
  }
}

/**
  * Represents the operation to retrieve a single graph via the graph-store protocol.
  * @param graphUri the graph IRI to get, if not specified this denotes the DEFAULT graph
  *
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-get]]
  */
case class GetGraph(graphUri: Option[URI]) extends GraphStoreRequest {
  final override val httpMethod: HttpMethod = GET
}


object DropGraphM {
  def unapply(arg: DropGraph): Option[(Option[URI], HttpMethod)] = {
    Some((arg.graphUri, arg.httpMethod))
  }
}

/**
  * Represents an operation to drop a single graph via the graph-store protocol.
  * @param graphUri the graph IRI to drop, if not specified this denotes the DEFAULT graph
  *
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-delete]]
  */
case class DropGraph(graphUri: Option[URI]) extends GraphStoreRequest {
  final override val httpMethod: HttpMethod = DELETE
}


/**
  * Represents an operation to insert graph(s) via the graph-store protocol.
  * @param graphUri    an optional graph IRI specifier, will be added as the value of the "?graph=uri"
  *                    query parameter
  * @param mergeGraphs merge if true, replace graph if false
  *
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-post]]
  * @see [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-put]]

  */
abstract class InsertGraph(val graphUri: Option[URI], val mergeGraphs: Boolean) extends GraphStoreRequest {

  override val httpMethod: HttpMethod = if (mergeGraphs) POST else PUT

  val modelFormat: RDFFormat
}

object InsertGraphFromModelM {

   def unapply(arg: InsertGraphFromModel): Option[(Model, RDFFormat, Option[URI], HttpMethod)] = {
    Some((arg.graphModel, arg.modelFormat, arg.graphUri, arg.httpMethod))
  }
}

/**
  * Used to insert an in-memory RDF model into the triple store.
  * @param graphModel  the graph model
  * @param graphUri    optional graph Uri, defaults to [[None]]
  * @param mergeGraphs indicates if the inserted data should be merged data already in the specified graph in
  *                    the database, if set to false the previously stored data in the graph will be replaced
  *                    with the payload, defaults to false
  */
case class InsertGraphFromModel
(
  graphModel: Model,
  override val graphUri: Option[URI] = None,
  override val mergeGraphs: Boolean = false
) extends InsertGraph(graphUri, mergeGraphs) {

  /**
    * We only use [[NTRIPLES]] upload format.
    */
  override val modelFormat: RDFFormat = NTRIPLES

}


object InsertGraphFromURLM {
  def unapply(arg: InsertGraphFromURL): Option[(URL, RDFFormat, Option[URI], HttpMethod)] = {
    Some((arg.url, arg.modelFormat, arg.graphUri, arg.httpMethod))
  }
}

/**
  * Used to insert RDF contents from a file or another resource into the triple store.
  * @param url         the URL of the file to use
  * @param modelFormat the RDF format to use, supporting all formats available in [[org.eclipse.rdf4j.rio.Rio]]
  * @param graphUri    an optional graph Uri, will insert into the default graph if no graph is specified.
  *                    NB: if the incoming RDF is in "quads" format, i.e. [[NQUADS]] or [[JSONLD]], the graph
  *                    in the RDF is ignored and instead the graphIri property is used.
  * @param mergeGraphs indicates if the inserted data should be merged data already in the specified graph in
  *                    the database, if set to false the previously stored data in the graph will be replaced
  *                    with the payload, defaults to false
  */
case class InsertGraphFromURL
(
  url: URL,
  modelFormat: RDFFormat,
  override val graphUri: Option[URI] = None,
  override val mergeGraphs: Boolean = false
) extends InsertGraph(graphUri, mergeGraphs)

object InsertGraphFromPathM {
  def unapply(arg: InsertGraphFromPath): Option[(Path, RDFFormat, Option[URI], HttpMethod)] = {
    Some((arg.path, arg.modelFormat, arg.graphUri, arg.httpMethod))
  }
}

/**
  * Used to insert RDF contents from a file or another resource into the triple store.
  * @param path        the local filesystem path of the file to use
  * @param modelFormat the RDF format to use, supporting all formats available in [[org.eclipse.rdf4j.rio.Rio]]
  * @param graphUri    an optional graph Uri, will insert into the default graph if no graph is specified.
  *                    NB: if the incoming RDF is in "quads" format, i.e. [[NQUADS]] or [[JSONLD]], the graph
  *                    in the RDF is ignored and instead the graphIri property is used.
  * @param mergeGraphs indicates if the inserted data should be merged data already in the specified graph in
  *                    the database, if set to false the previously stored data in the graph will be replaced
  *                    with the payload, defaults to false
  */
case class InsertGraphFromPath
(
  path: Path,
  modelFormat: RDFFormat,
  override val graphUri: Option[URI] = None,
  override val mergeGraphs: Boolean = false
) extends InsertGraph(graphUri, mergeGraphs)
