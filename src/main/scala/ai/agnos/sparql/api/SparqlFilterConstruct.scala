package ai.agnos.sparql.api


import org.eclipse.rdf4j.model.{BNode, IRI, Literal, Value}

object SparqlFilterConstruct extends SparqlConstructFactory {

  def apply(filters: Map[IRI, Value],
            propertyIRIs: Seq[IRI] = Nil,
            graphIRIs: Seq[IRI] = Nil,
            reasoningEnabled: Boolean = false)(
             implicit _paging: PagingParams = NoPaging
           ): SparqlConstruct = {

    // TODO make PrefixMapping a parameter
    new SparqlConstruct()(PrefixMapping.standard) {

      private lazy val whereClause = {
        s"""
           |WHERE {
           |  ${values("graphIri", graphIRIs)}
           |  GRAPH ?graphIri {
           |    $filterClause
           |    ${values("propertyIri", propertyIRIs)}
           |    ?resourceIri ?propertyIri ?value .
           |  }
           |}
         """.stripMargin
      }

      private val filterClause: String = {
        filters.map (filter =>
          s"?resourceIri ${strInSparql(filter._1)} ${strInSparql(filter._2)} ."
        ).mkString("\n    ")
      }

      override val statement: String = build(graphConstructReified(whereClause, _paging))

      override val reasoning: Boolean = reasoningEnabled
    }
  }

}
