package com.modelfabric.sparql.stream.client

import akka.http.scaladsl.model.{ContentType, HttpCharsets, MediaType}

/*           */
/* CONSTANTS */
/* --------- */
// JC: these are same for all triple store??
//SSZ: AFAIK yes: https://www.w3.org/TR/sparql11-protocol/
object SparqlClientConstants {
  val QUERY_URI_PART = "/query"
  val QUERY_PARAM_NAME = "query"
  val REASONING_PARAM_NAME = "reasoning"

  val UPDATE_URI_PART = "/update"
  val UPDATE_PARAM_NAME = "update"

  val GRAPH_PARAM_NAME = "graph"

  val DEFAULT_PARAM_NAME = "default"

  val FORM_MIME_TYPE = "x-www-form-urlencoded"
  val SPARQL_RESULTS_MIME_TYPE = "sparql-results+json"
  val TEXT_BOOLEAN_MIME_TYPE = "boolean"

  /**
    * Media type for Form upload
    */
  val `application/x-www-form-urlencoded`: ContentType.NonBinary =
    MediaType.applicationWithFixedCharset(
      FORM_MIME_TYPE,
      HttpCharsets.`UTF-8`
    ).toContentType

  /**
    * Media type for Sparql JSON protocol
    */
  val `application/sparql-results+json`: ContentType.NonBinary =
    MediaType.applicationWithFixedCharset(
      SPARQL_RESULTS_MIME_TYPE,
      HttpCharsets.`UTF-8`
    ).toContentType

  /**
    * Media type for text/boolean
    */
  val `text/boolean`: ContentType.NonBinary =
    MediaType.text(TEXT_BOOLEAN_MIME_TYPE).toContentType(HttpCharsets.`UTF-8`)

  /**
    * Content Type for application/ld+json
    */
  val `application/ld+json`: ContentType.NonBinary = MediaType.applicationWithFixedCharset("ld+json", HttpCharsets.`UTF-8`).toContentType

  /**
    * Content Type for text/x-nquads and application/n-quads
    */
  val `text/x-nquads`: ContentType.NonBinary = MediaType.text("x-nquads").toContentType(HttpCharsets.`UTF-8`)
  val `application/n-quads`: ContentType.NonBinary = MediaType.applicationWithFixedCharset("n-quads", HttpCharsets.`UTF-8`).toContentType

  /**
    * Content Type for application/n-triples
    */
  val `application/n-triples`: ContentType.NonBinary = MediaType.applicationWithFixedCharset("n-quads", HttpCharsets.`UTF-8`).toContentType

  /**
    * Content Type for text/turtle
    */
  val `text/turtle`: ContentType.NonBinary = MediaType.text("turtle").toContentType(HttpCharsets.`UTF-8`)

  /**
    * Checks the number of available CPU cores from the JVM runtime. Used parallelise async stream operations.
    */
  lazy val numberOfCpuCores: Int = sys.runtime.availableProcessors()

}
