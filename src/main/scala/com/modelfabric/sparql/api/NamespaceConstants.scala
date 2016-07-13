package com.modelfabric.sparql.api

/**
 * Defines the most generic namespaces as constants
 */
// JC: it's only used in PrefixMapping. I think it's better move this object there
object NamespaceConstants {

  val PREFIX_RDFS = "rdfs"
  val PREFIX_RDF = "rdf"
  val PREFIX_DC = "dc"
  val PREFIX_OWL = "owl"
  val PREFIX_XSD = "xsd"
  val PREFIX_SKOS = "skos"
  val PREFIX_TIME = "time"
  val PREFIX_FOAF = "foaf"
  val PREFIX_TERMS = "terms"
  val PREFIX_MFAB_DATA = "mfab"

  val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  val RDFS = "http://www.w3.org/2000/01/rdf-schema#"
  val RDFSyntax = "http://www.w3.org/TR/rdf-syntax-grammar#"
  val OWL = "http://www.w3.org/2002/07/owl#"
  val DC_11 = "http://purl.org/dc/elements/1.1/"
  val TERMS = "http://purl.org/dc/terms/"
  val XSD = "http://www.w3.org/2001/XMLSchema#"
  val SKOS = "http://www.w3.org/2004/02/skos/core#"
  val TIME = "http://www.w3.org/2006/time#"
  val FOAF = "http://xmlns.com/foaf/0.1/"
  val MFAB_DATA = "http://data.modelfabric.com/resource/"
}
