package com.modelfabric.sparql.mapper

import com.modelfabric.sparql.client.QuerySolution

/**
 * Extracts a string value from a query solution object.
 */
class StringSolutionMapper(fieldName : String)
    extends SolutionMapper[String] {

  def map(querySolution : QuerySolution) : String =
    querySolution.string(fieldName).get
}
