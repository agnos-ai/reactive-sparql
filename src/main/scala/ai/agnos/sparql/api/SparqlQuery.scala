package ai.agnos.sparql.api

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.eclipse.rdf4j.model.{IRI, Value}
import ai.agnos.sparql.stream.client.SparqlClientConstants._
import ai.agnos.sparql.util.SparqlQueryStringConverter

trait QueryType

/**
  * For queries that return a streamed source of the triple stores response directly, which is
  * suitable for large batch jobs and long lasting operations.
  */
case class StreamedQuery(desiredContentType: ContentType = `application/sparql-results+json`) extends QueryType

trait MappedQuery[T <: SparqlResult] extends QueryType {
  def mapper: ResultMapper[T]
}

/**
  * Default Mapped Query, mapping to ResultSet object.
  */
case object DefaultMappedQuery extends MappedQuery[ResultSet] {
  val mapper: ResultMapper[ResultSet] = ResultSetMapper
}

/**
  * Client-provided result mapper.
  *
  * @param mapper
  * @tparam T
  */
case class ClientMappedQuery[T <: SparqlResult](mapper: ResultMapper[T]) extends MappedQuery[T]

/**
  * SPARQL Query to be executed, supports both mapped and streaming queries.
  *
  * The streaming option also allows for full akka-streams integration support with the knowledge graph, which is
  * useful for large results (e.g. reports)
  *
  * @param query the query to be executed
  * @param queryType the type of query, defaults to [[DefaultMappedQuery]]
  * @param bindings the request bindings
  * @param defaultGraphs the default graphs passed as query parameters (correspond to FROM &lt;graph&gt; SPARQL statement)
  * @param namedGraphs the named graphs passed as query parameters (correspont to FROM NAMED &lt;graph&gt; SPARQL statement)
  * @param limit an optional limit to be passed as a query parameter
  * @param offset an optional offset to be passed as a query parameter
  * @param reasoning an optional reasoning flag to be passed as a query parameter
  * @param timeout an optional per-query specific timeout to be passed as as query parameter
  */
case class SparqlQuery
(
  query: String,
  httpMethod: HttpMethod = HttpMethods.GET,
  queryType: QueryType = DefaultMappedQuery,
  bindings: Map[String, Value] = Map.empty,
  defaultGraphs: List[IRI] = Nil,
  namedGraphs: List[IRI] = Nil,
  limit: Option[Long] = None,
  offset: Option[Long] = None,
  reasoning: Option[Boolean] = None,
  timeout: Option[Long] = None
)(implicit _pm: PrefixMapping) extends SparqlStatement()(_pm) {
  lazy override val statement: String = build(query)

  import HttpMethods._

  /**
    * The threshold at which the SPARQL client will force to use HTTP
    * method on the Sparql endpoint. This is due to HTTP limiting the size of the query string.
    *
    * Defaults to 2kB, but may be overridden by setting the SPARQL_CLIENT_MAX_HTTP_QUERY_URI_LENGTH
    * environment variable to the desired value.
    *
    * @see [[https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.2.1]]
    */
  val sparqlQueryPostThreshold: Int = sys.env.get("SPARQL_CLIENT_MAX_HTTP_QUERY_URI_LENGTH").map(_.toInt).getOrElse(2048)

  /**
    * Decides which method to use for the query. Will return the desired method, unless the
    * query is longer that the POST threshold configured by [[sparqlQueryPostThreshold]], in which case
    * POST is returned
    * @return the decided HTTP method to be used
    */
  val queryHttpMethod: HttpMethod = {
    (statement, httpMethod) match {
      case (_, POST)                                     => POST
      case (s, _) if s.length > sparqlQueryPostThreshold => POST
      case (_, m) => m
    }
  }

  lazy val encodedQueryString: String = {
    SparqlQueryStringConverter.toQueryString(this)
  }


}

case class StreamingSparqlResult(dataStream: Source[ByteString, Any], contentType: Option[ContentType]) extends SparqlResult
