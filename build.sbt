import sbt.Keys._

organization := "com.briskware"

name := "briskware-sparql"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.13"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.13" % "test"

libraryDependencies += "com.modelfabric" %% "scala-utils" % "0.1-SNAPSHOT"

libraryDependencies += "io.spray" % "spray-json_2.11" % "1.3.2"

libraryDependencies += "io.spray" %% "spray-client" % "1.3.3"

libraryDependencies += "javax.ws.rs" % "jsr311-api" % "1.1.1"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.5" % "test"

libraryDependencies += "com.sun.jersey" % "jersey-core" % "1.19"

libraryDependencies += "com.sun.jersey" % "jersey-client" % "1.19"

//libraryDependencies += "org.apache.jena" % "jena-core" % "3.0.0"
//
//libraryDependencies += "org.apache.jena" % "jena-arq" % "3.0.0"

//
// ScalaStyle Settings
//
org.scalastyle.sbt.ScalastylePlugin.Settings

org.scalastyle.sbt.PluginKeys.config <<= baseDirectory { _ / "conf" / "scalastyle-config.xml" }





