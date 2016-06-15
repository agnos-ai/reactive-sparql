package com.modelfabric.client.sparql.spray.mapper

import com.modelfabric.client.sparql.spray.client.QuerySolution


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
