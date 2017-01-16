package com.modelfabric.sparql.mapper

import com.modelfabric.sparql.api.QuerySolution


/**
 * A helper mapper that delegates mapping to a specified function.
 */
class DelegatingSolutionMapper[T] private (mapper : QuerySolution ⇒ T)
    extends SolutionMapper[T] {

  def map(querySolution : QuerySolution) : T = {
    mapper(querySolution)
  }
}

object DelegatingSolutionMapper {
  def apply[T](mapper : QuerySolution ⇒ T) = new DelegatingSolutionMapper[T](mapper)
}
