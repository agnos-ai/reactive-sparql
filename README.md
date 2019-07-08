reactive-sparql
===============

*"A Reactive SPARQL Client for Scala and Akka"*

This client uses [akka-streams](http://doc.akka.io/docs/akka/2.4/scala.html) to do as much as possible asynchronously, with back pressure
support around the HTTP connection towards the triple store. There are no blocking calls crossing process boundaries.

The older Spray HTTP client no longer supported, however it is still available as
release [v0.1.3](https://github.com/agnos-ai/reactive-sparql/tree/v0.1.3).

The akka-streams APIs currently supports 3 flavours of flows:

* [Flavour #1](#flavour-1-run-sparql): Execute SPARQL
* [Flavour #2](#flavour-2-construct-models): Construct Models
* [Flavour #3](#flavour-3-manipulate-graphs): Manipulate Graphs

### Flavour #1: Run SPARQL

Use the `SparqlQuery(stmt: String)` or `SparqlUpdate(stmt: String)` case class and embed it in a `SparqlRequest()` to be passed to the flow. On the other end a
`SparqlResponse()` pops out. Support for custom mappings is available, where the resulting values get marshaled to a custom domain object.
This is however not mandatory, there is a default result mapper available that will return a [standard
result set model](src/main/scala/ai/agnos/sparql/api/SparqlResult.scala#L25) based on the `application/sparql-results+json` content type.

It is possible to use a single wrapper flow of [`Flow[SparqlRequest, SparqlResponse, _]`](src/main/scala/ai/agnos/sparql/stream/client/SparqlRequestFlowBuilder.scala)
to run both `SparqlUpdate()` and `SparqlQuery()` statements. There is an option to use specialised [query](src/main/scala/ai/agnos/sparql/stream/client/SparqlQueryFlowBuilder.scala)
and [update](src/main/scala/ai/agnos/sparql/stream/client/SparqlUpdateFlowBuilder.scala) flows as well.

The underlying implementation communicates with the triple store via the HTTP endpoints, as documented here
for [queries](https://www.w3.org/TR/2013/REC-sparql11-query-20130321/)
and [updates](https://www.w3.org/TR/2013/REC-sparql11-update-20130321/).

#### Example #1: Run a simple Sparql query

```scala
/* Define domain case class and mappings */
object Person extends ResultMapper[Person] {
  override def map(qs: QuerySolution): Person = {
    Person(qs.uri("g").get, qs.string("c").get)
  }
}
case class Person(id: URI, name: String) extends SparqlResult

/* Create a bespoke SparqlQuery with a mapping to a Person */
val mappingQuery2Get = SparqlQuery( """
  |SELECT ?g ?b ?c
  |FROM NAMED <urn:test:agnos:data>
  |WHERE {
  |  GRAPH ?g {
  |   <urn:test:whatever> ?b ?c
  |  }
  |}""", mapping = Person, reasoningEnabled = true)

/* Create the Flow and Probes */
val sparqlRequestFlowUnderTest = SparqlRequestFlowBuilder.sparqlRequestFlow(testServerEndpoint)
val (source, sink) = TestSource.probe[SparqlRequest]
  .via(sparqlRequestFlowUnderTest)
  .toMat(TestSink.probe[SparqlResponse])(Keep.both)
  .run()

/* Send the request to the stream and expect the result */
sink.request(1)
source.sendNext(SparqlRequest(mappingQuery2Get))
sink.expectNext(receiveTimeout) match {
  case SparqlResponse(_, true, results, None) =>
    val persons: Seq[Person] = results //the  mapped collection is returned
    assert(persons.contains(...)
  case r@_ =>
    fail(r)
}
```

### Flavour #2: Construct Models

Working with Sparql query solutions (rows of result bindings as returned by a SELECT statement) is not always suitable. This is because the result
is not plain RDF.

Use of [SPARQL CONSTRUCT](https://www.w3.org/TR/sparql11-query/#construct)s is suitable in cases where we are only interested in triples (i.e. not
quads, where the graph IRI is missing)

At the moment there is no way to write the following statement, so that the resulting RDF is returned in "quads" format (N-QUADS or JSON-LD)
```sparql
CONSTRUCT {
  GRAPH ?g {
    ?s ?p ?o .
  }
} WHERE {
...
}
```

This flow has been created to circumvent the problem. It is an extension of the API used in [Flavour #1](#flavour-1-run-sparql).

Instead of a `SparqlQuery()` this flow works with a `SparqlConstruct()` inside the `SparqlRequest()`
```scala
object SparqlConstruct {
  def apply(resourceIRIs: Seq[URI] = Nil,
            propertyIRIs: Seq[URI] = Nil,
            graphIRIs: Seq[URI] = Nil,
            reasoningEnabled: Boolean = false)(
    implicit _paging: PagingParams = NoPaging
  ): SparqlConstruct = {
    ...
  }
}
```
By specifying a set of matching resource, property and/or graph IRIs, we limit the number of results that are returned.
Internally this flow will generate a reified SELECT statement that allows us to capture all 4 properties of the RDF Model, including the graph IRI.

The flow responds with a `SparqlModelResult(model: Model)` within a `SparqlResult()` which contains the RDF4J Model (Graph) instance.
```scala
case class SparqlModelResult(model: Model) extends SparqlResult
```

Refer to [`Flow[SparqlRequest, SparqlResponse, _]`](src/main/scala/ai/agnos/sparql/stream/client/SparqlConstructToModelFlowBuilder.scala)
for more detail.

### Flavour #3: Manipulate Graphs

This flow allows for basic graph manipulation, as defined by the [graph-store protocol](https://www.w3.org/TR/2013/REC-sparql11-http-rdf-update-20130321/).
Not all aspects of the protocol are supported, however it is possible to:

#### Retrieve Graphs

Retrieve an entire graph using `GetGraph(graphUri: Option[URI])` wrapped in a `GraphStoreRequest()`

If no graphIri is specified the query returns the contents of the DEFAULT graph.

#### Drop Graphs

Drop an entire graph using `DropGraph(graphUri: Option[URI])` wrapped in a `GraphStoreRequest()`

If no graphIri is specified the request drops the DEFAULT graph, so be careful with that if you don't use named graphs in your triple store.

#### Insert Models into Graphs

Insert the contents of an RDF Model into the specified graph. There are 3 variants:

* `InsertGraphFromModel(graphModel: Model, graphUri: Option[URI])`: inserts an in-memory RDF Model;
* `InsertGraphFromPath(filePath: Path, graphUri: Option[URI], format: RDFFormat)`: inserts the contents of the specified file in the given RDF format;
* `InsertGraphFromURL(url: URL, format: RDFFormat, graphUri: Option[URI])`: inserts the contents of the file behind the specified HTTP URL in the given RDF format.

All the operations above return a `GraphStoreResponse` which contains the success status of the operation and a optional model (for `GetGraph()` queries only)
```scala
case class GraphStoreResponse
(
  request: GraphStoreRequest,
  success: Boolean,
  statusCode: Int,
  statusText: String,
  model: Option[Model] = None
)
```

There is a `mergeGraphs: Boolean` parameter for all insert messages, that allows us to control how the resulting graph will deal with
the newly inserted triples.

* `mergeGraphs = true` will perform a HTTP PUT operation, which merges the content of the graph being sent with the graph that
  is already in the triple store;
* `mergeGraphs = false` is the DEFAULT option and will perform a HTTP POST operation, which replaces the content of the graph with
  the one being sent over.

If no graph is specified, the insert will use the DEFAULT graph in the triple store.

Refer to [`Flow[GraphStoreRequest, GraphStoreResponse, _]`](src/main/scala/ai/agnos/sparql/stream/client/GraphStoreRequestFlowBuilder.scala)
for more detail.
