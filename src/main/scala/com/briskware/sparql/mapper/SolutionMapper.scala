package com.briskware.sparql.mapper

import com.briskware.sparql.client.QuerySolution

/**
 * Implementations should be able to extract data from a query solution
 * and create a mandatory object. This may be a map of field and values
 * or case class.
 */
trait SolutionMapper[T] {

  /**
   * Extracts data from the query solution.
   *
   * @param querySolution solution containing the data.
   * @return the extracted data.
   */
  def map(querySolution : QuerySolution) : T
}

