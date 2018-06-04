package org.modelfabric.sparql.mapper

import org.modelfabric.sparql.api.QuerySolution

/**
 * Extracts a string value from a query solution object.
 */
class StringSolutionMapper(fieldName : String)
    extends SolutionMapper[String] {

  def map(querySolution : QuerySolution) : String =
    querySolution.string(fieldName).get
}
