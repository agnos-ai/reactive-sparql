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
}
