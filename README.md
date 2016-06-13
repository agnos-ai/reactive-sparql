reactive-sparql
===============

A Reactive Scala SPARQL Client.

This client uses Akka to do as much as possible asynchronously. It currently works via on an older Spray HTTP client, 
but we're currently migrating it to work akka-streams, so we will use akka-http instead (see our [akka-streams](https://github.com/modelfabric/reactive-sparql/tree/akka-streams) branch)

