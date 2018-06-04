package org.modelfabric.sparql.mapper

import org.modelfabric.sparql.api.QuerySolution
import org.eclipse.rdf4j.model.IRI

/**
 * The mapper expects that a query solution has a IRI field.
 * It maps the solution to that field.
 */
class IRISolutionMapper(fieldName : String) extends SolutionMapper[IRI] {

  def map(querySolution : QuerySolution) : IRI = querySolution.asValueMap(fieldName).asIri
}
