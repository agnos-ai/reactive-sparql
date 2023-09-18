import sbt._
import Keys._
import scoverage.ScoverageKeys._

import scala.util.Try

import Dependencies._

val buildOrganization = "ai.agnos"
val buildScalaVersion = Version.scala
val buildExportJars = true

val buildSettings = Seq(
  organization := buildOrganization,
  scalaVersion := buildScalaVersion,
  exportJars := buildExportJars,
  updateOptions := updateOptions.value.withCachedResolution(true),
  shellPrompt := { state =>
    "sbt [%s]> ".format(Project.extract(state).currentProject.id)
  },
  scalacOptions := Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-target:jvm-1.8",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Xlint"
  ),
  javaOptions := Seq(
    "--add-exports java.xml/com.sun.org.apache.xerces.internal.util=ALL-UNNAMED"
  ),
  parallelExecution in Test := false,
  coverageFailOnMinimum := true,
  coverageOutputHTML := true,
  coverageOutputXML := true
) ++ Defaults.itSettings

lazy val project = Project("reactive-sparql", file("."))
  .configs(IntegrationTest)
  .settings(buildSettings: _*)
  .settings(name := "reactive-sparql")
  .settings(libraryDependencies ++= `reactive-sparql-dependencies`)
  .settings(coverageMinimum := 62.0d)
