package com.modelfabric.sparql.query

import com.modelfabric.sparql.{SparqlUpdate, PrefixMapping}
import java.net.URI

/**
 * Deletes all triples that have the specified uri as a subject or an object.
 */
case class Delete(uri : URI)
    extends SparqlUpdate()(PrefixMapping.all) {

  override def statement = build(s"""
  |WITH <urn:test:modelfabric:data>
  |DELETE {
  |  ?s ?p <$uri> .
  |}
  |WHERE {
  |  ?s ?p <$uri> .
  |} ;
  |WITH <tag:bb:data>
  |DELETE {
  |  <$uri> ?p ?o .
  |}
  |WHERE {
  |  <$uri> ?p ?o .
  |} ;
  """)
}
