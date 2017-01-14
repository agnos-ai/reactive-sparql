package com.modelfabric.sparql.api

import java.net.{URI, URL}

import com.modelfabric.sparql.api.HttpMethod.{DELETE, POST, PUT}
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.RDFFormat.{JSONLD, NQUADS, TURTLE}


/**
  * Implementation of the graph-store API messages.
  */
trait GraphStoreProtocol extends ClientAPIProtocol

abstract class GraphStoreRequest extends ClientHttpRequest with GraphStoreProtocol

/**
  * The response entity for graph store operations
  * @param request the request object
  * @param success true if the operation was successful
  * @param statusCode the HTTP status code of the response
  * @param statusText the HTTP status reason text
  */
case class GraphStoreResponse
(
  request: GraphStoreRequest,
  success: Boolean,
  statusCode: Int,
  statusText: String
) extends ClientHttpResponse with GraphStoreProtocol

object DropGraph {

  /**
    * Create a [[DropGraph]] request object
    * @param graphUri the graph to drop
    * @return
    */
  def apply(graphUri: Option[URI]): DropGraph = {
    new DropGraph(graphUri)
  }

  def unapply(arg: DropGraph): Option[(Option[URI], HttpMethod)] = {
    Some((arg.graphUri, arg.httpMethod))
  }

}

/**
  * Represents an operation to drop a single graph via the graph-store protocol:
  * [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-delete]]
  * @param graphUri the graph IRI to drop, if not specified this denotes the DEFAULT graph
  */
class DropGraph(val graphUri: Option[URI]) extends GraphStoreRequest {

  final override val httpMethod: HttpMethod = DELETE

}


/**
  * Represents an operation to insert graph(s) via the graph-store protocol:
  * [[https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/#http-post]]
  * @param graphUri an optional graph IRI specifier, will be added as the value of the "?graph=uri"
  *                 query parameter
  * @param mergeGraphs merge if true, replace graph if false.
  */
abstract class InsertGraph(graphUri: Option[URI], mergeGraphs: Boolean) extends GraphStoreRequest {

  override val httpMethod: HttpMethod = if (mergeGraphs) POST else PUT

  val modelSerializationFormat: RDFFormat
}

object InsertGraphFromModel {

  /**
    * Create an [[InsertGraphFromModel]] request object
    *
    * @param graphModel the graph model
    * @param graphUri   optional graph Iri, if not specified, the graphs (contexts) from the passed model may be used.
    * @param merge indicates if the inserted data should be merged data already in the specified graph in the database,
    *              if set to false the previously stored data in the graph will be replaced with the payload.
    * @return
    */
  def apply(graphModel: Model, graphUri: Option[URI], merge: Boolean): InsertGraphFromModel = {
    new InsertGraphFromModel(graphModel, graphUri, merge)
  }

  def apply(graphModel: Model, merge: Boolean): InsertGraphFromModel = {
    new InsertGraphFromModel(graphModel, None, merge)
  }

  def unapply(arg: InsertGraphFromModel): Option[(Model, RDFFormat, Option[URI], HttpMethod)] = {
    Some((arg.graphModel, arg.modelSerializationFormat, arg.graphUri, arg.httpMethod))
  }
}

/**
  * Used to insert an in-memory RDF model into the triple store.
  * @param graphModel the graph model
  * @param graphUri optional graph Uri
  * @param mergeGraphs merge if true, replace graph if false.
  */
class InsertGraphFromModel
(
  val graphModel: Model,
  val graphUri: Option[URI],
  mergeGraphs: Boolean
) extends InsertGraph(graphUri, mergeGraphs) {

  /**
    * We only use [[NQUADS]] upload format if the graph model contains graph URIs (contexts)
    */
  override val modelSerializationFormat: RDFFormat = {
    graphModel.contexts() match {
      case m if m.isEmpty => TURTLE
      case _              => NQUADS
    }
  }
}


object InsertGraphFromURL {
  /**
    *
    * @param url the location of the file to be loaded
    * @param format the format of the RDF payload
    * @param graphUri the graph URI
    * @param merge indicates if the inserted data should be merged data already in the specified graph in the database,
    *              if set to false the previously stored data in the graph will be replaced with the payload.
    * @return
    */
  def apply(url: URL, format: RDFFormat, graphUri: Option[URI], merge: Boolean): InsertGraphFromURL = {
    new InsertGraphFromURL(url, format, graphUri, merge)
  }

  def apply(url: URL, format: RDFFormat, merge: Boolean): InsertGraphFromURL = {
    new InsertGraphFromURL(url, format, None, merge)
  }

  def unapply(arg: InsertGraphFromURL): Option[(URL, RDFFormat, Option[URI], HttpMethod)] = {
    Some((arg.url, arg.modelSerializationFormat, arg.graphUri, arg.httpMethod))
  }

}

/**
  * Used to insert RDF contents from a file or another resource into the triple store.
  * @param url the URL of the file to use
  * @param modelSerializationFormat the RDF format to use, supporting all formats available
  *                                 in [[org.eclipse.rdf4j.rio.Rio]]
  * @param graphUri an optional graph Uri, will insert into the default graph if no graph is specified
  *                 and the file is not in "quads" format [[TURTLE]], [[JSONLD]]
  * @param mergeGraphs merge if true, replace graph if false.
  */
class InsertGraphFromURL
(
  val url: URL,
  val modelSerializationFormat: RDFFormat,
  val graphUri: Option[URI],
  mergeGraphs: Boolean
) extends InsertGraph(graphUri, mergeGraphs)
