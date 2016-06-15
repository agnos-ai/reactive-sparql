package com.modelfabric.client.sparql.spray.mapper

import com.modelfabric.client.sparql.spray.client.QuerySolution

/**
 * Default mapper doesn't map query solutions. It returns unmodified solutions.
 */
object DefaultSolutionMapper
    extends SolutionMapper[QuerySolution] {

  def map(querySolution : QuerySolution) : QuerySolution = querySolution
}
