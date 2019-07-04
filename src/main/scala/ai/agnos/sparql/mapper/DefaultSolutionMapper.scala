package ai.agnos.sparql.mapper

import ai.agnos.sparql.api.QuerySolution

object DefaultSolutionMapper {
  def apply() = new DefaultSolutionMapper {}
}

/**
 * Default mapper doesn't map query solutions. It returns unmodified solutions.
 */
trait DefaultSolutionMapper
    extends SolutionMapper[QuerySolution] {

  def map(querySolution : QuerySolution) : QuerySolution = querySolution
}
