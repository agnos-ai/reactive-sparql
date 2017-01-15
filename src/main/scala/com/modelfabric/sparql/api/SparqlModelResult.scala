package com.modelfabric.sparql.api

import org.eclipse.rdf4j.model.Model

/**
  * A result representing an RDF Model
  * @param model the RDF model
  */
case class SparqlModelResult(model: Model) extends SparqlResult
