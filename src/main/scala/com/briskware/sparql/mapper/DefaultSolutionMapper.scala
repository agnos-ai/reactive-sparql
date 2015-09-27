package com.briskware.sparql.mapper

import com.briskware.sparql.client.QuerySolution

/**
 * Default mapper doesn't map query solutions. It returns unmodified solutions.
 */
object DefaultSolutionMapper
    extends SolutionMapper[QuerySolution] {

  def map(querySolution : QuerySolution) : QuerySolution = querySolution
}
