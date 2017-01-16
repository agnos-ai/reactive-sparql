package com.modelfabric.sparql.mapper

import java.net.URI

import com.modelfabric.sparql.api.QuerySolution

/**
 * The mapper expects that a query solution has a URI field.
 * It maps the solution to that field.
 */
class URISolutionMapper(fieldName : String) extends SolutionMapper[URI] {

  def map(querySolution : QuerySolution) : URI = querySolution.asValueMap(fieldName).asUri
}
