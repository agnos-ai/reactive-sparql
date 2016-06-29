package com.modelfabric.sparql.mapper

import com.modelfabric.sparql.api.QuerySolution

/**
 * Default mapper doesn't map query solutions. It returns unmodified solutions.
 */
object DefaultSolutionMapper
    extends SolutionMapper[QuerySolution] {

  def map(querySolution : QuerySolution) : QuerySolution = querySolution
}
