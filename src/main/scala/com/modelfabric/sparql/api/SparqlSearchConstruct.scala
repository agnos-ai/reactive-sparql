package com.modelfabric.sparql.api

import org.eclipse.rdf4j.model.IRI

object SparqlSearchConstruct extends SparqlConstructFactory {

  def apply(resourceIRIs: Seq[IRI] = Nil,
            propertyIRIs: Seq[IRI] = Nil,
            searchString: String = "*",
            graphIRIs: Seq[IRI] = Nil,
            reasoningEnabled: Boolean = false)(
             implicit _paging: PagingParams = NoPaging
           ): SparqlConstruct = {
    new SparqlConstruct()(PrefixMapping.standard) {

      private val searchParams: String = {
        _paging.limit.map(o => s"'${searchString}' $o").getOrElse(s"'${searchString}' 10")

      }

      private val whereClause = {
        s"""
           |WHERE {
           |  ${values("graphIri", graphIRIs)}
           |  GRAPH ?graphIri {
           |    ${values("resourceIri", resourceIRIs)}
           |    ${values("propertyIri", propertyIRIs)}
           |    ?resourceIri ?propertyIri ?value .
           |    (?value ?score) <tag:stardog:api:property:textMatch> (${searchParams}) .
           |  }
           |}
         """.stripMargin
      }


      override val statement: String = build(graphConstructReified(whereClause, _paging))

      override val reasoning: Boolean = reasoningEnabled
    }
  }

}



