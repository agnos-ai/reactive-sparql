import sbt._
import Keys._
import scoverage.ScoverageKeys._

import scala.util.Try

import Dependencies._

val buildOrganization = "org.modelfabric"
val buildScalaVersion = Version.scala
val buildExportJars   = true

val buildSettings = Seq (
  organization  := buildOrganization,
  scalaVersion  := buildScalaVersion,
  exportJars    := buildExportJars,
  updateOptions := updateOptions.value.withCachedResolution(true),
  shellPrompt   := { state => "sbt [%s]> ".format(Project.extract(state).currentProject.id) },
  scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-language:implicitConversions", "-language:postfixOps", "-Xlint"),
  parallelExecution in Test := false,
  coverageFailOnMinimum := true,
  coverageOutputHTML    := true,
  coverageOutputXML     := true
) ++ Defaults.itSettings


lazy val project = Project("reactive-sparql", file("."))
  .configs(IntegrationTest)
  .settings(buildSettings: _*)
  .settings(name := "reactive-sparql")
  .settings(libraryDependencies ++= `reactive-sparql-dependencies`)
  .settings(coverageMinimum := 65.0D)
