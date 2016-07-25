reactive-sparql
===============

# A Reactive Scala SPARQL Client.

This client uses Akka to do as much as possible asynchronously. It currently works via on an older Spray HTTP client, 
but we're currently migrating it to work akka-streams, so we will use akka-http instead (see our [akka-streams](https://github.com/modelfabric/reactive-sparql/tree/akka-streams) branch)

# Usage

```scala
/* Prepare the implicit environment for Akka-Streams */
implicit val system = ActorSystem("test-system")
implicit val materializer = ActorMaterializer()
implicit val executionContext = system.dispatcher
implicit val prefixMapping = PrefixMapping.none
val receiveTimeout: FiniteDuration = 3 seconds

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
  |FROM NAMED <urn:test:mfab:data>
  |WHERE {
  |  GRAPH ?g {
  |   <urn:test:whatever> ?b ?c
  |  }
  |}""", mapping = Person) :: Nil

/* Create the Flow and Probes */
val sparqlRequestFlowUnderTest = Builder.sparqlRequestFlow(testServerEndpoint)
val (source, sink) = TestSource.probe[SparqlRequest]
  .via(sparqlRequestFlowUnderTest)
  .toMat(TestSink.probe[SparqlResponse])(Keep.both)
  .run()

/* Send the request to the stream and expect the result */
sink.request(1)
source.sendNext(SparqlRequest(mappingQuery2Get))
sink.expectNext(receiveTimeout) match {
  case SparqlResponse (_, true, mappedQuery2Result, None) => assert(true)
  case r@SparqlResponse(_, _, _, _) => assert(false, r)
}
```
