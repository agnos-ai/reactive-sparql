import sbt._
import Keys._
import spray.revolver.RevolverPlugin._
import scoverage.ScoverageKeys._

import scala.util.Try


object BuildSettings {
  val buildOrganization = "com.modelfabric"
  val buildScalaVersion = Version.scala
  val buildExportJars   = true

  val buildSettings = Seq (
    organization  := buildOrganization,
    scalaVersion  := buildScalaVersion,
    exportJars    := buildExportJars,
    updateOptions := updateOptions.value.withCachedResolution(true),
    shellPrompt := { state => "sbt [%s]> ".format(Project.extract(state).currentProject.id) },
    scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-language:implicitConversions", "-language:postfixOps", "-Xlint"),
    incOptions    := incOptions.value.withNameHashing(nameHashing     = true),
    ivyScala      := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    parallelExecution in Test := false,
    coverageFailOnMinimum := true,
    coverageOutputHTML    := true,
    coverageOutputXML     := true
  ) ++ Defaults.itSettings ++ Revolver.settings
}



object PublishingSettings {

  val snapshotRepository = Try("snapshots" at sys.env("REPOSITORY_SNAPSHOTS")).toOption
  val releaseRepository =  Try("releases"  at sys.env("REPOSITORY_RELEASES" )).toOption

  val jenkinsMavenSettings = Seq(
    publishMavenStyle       := true,
    publishArtifact in Test := false,
    pomIncludeRepository    := { _ => false },
    publishTo := {
      if (isSnapshot.value) {
        snapshotRepository
      } else {
        releaseRepository
      }
    },
    pomExtra := {
      <scm>
        <url>https://github.com/modelfabric/reactive-sparql.git</url>
        <connection>scm:git:https://github.com/modelfabric/reactive-sparql.git</connection>
      </scm>
      <developers>
        <developer>
          <id>jgeluk</id>
          <name>Jacobus Geluk</name>
          <url>https://github.com/orgs/modelfabric/people/jgeluk</url>
        </developer>
        <developer>
          <id>JianChen123</id>
          <name>Jian Chen</name>
          <url>https://github.com/orgs/modelfabric/people/JianChen123</url>
        </developer>
        <developer>
          <id>szaniszlo</id>
          <name>Stefan Szaniszlo</name>
          <url>https://github.com/orgs/modelfabric/people/szaniszlo</url>
        </developer>
      </developers>
    }
  )
}

object Version {

  val scala      = "2.12.4"
  val scalaUtils = "0.6"
  val akka       = "2.5.7"
  val akkaHttp   = "10.0.10"
  val javaxWsRs  = "1.1.1"
  val jersey     = "1.19"
  val rdf4j      = "2.1.2"
  val logback    = "1.1.4"
  val scalaTest  = "3.0.1"
  val fuseki     = "2.6.0"
}


object Library {
  
  val akkaActor         = "com.typesafe.akka" %% "akka-actor"                        % Version.akka
  val akkaStream        = "com.typesafe.akka" %% "akka-stream"                       % Version.akka
  val akkaHttpCore      = "com.typesafe.akka" %% "akka-http-core"                    % Version.akkaHttp
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json"              % Version.akkaHttp
  val akkaSlf4j         = "com.typesafe.akka" %% "akka-slf4j"                        % Version.akka
  val javaxWsRs         = "javax.ws.rs"       %  "jsr311-api"                        % Version.javaxWsRs
  val jerseyCore        = "com.sun.jersey"    %  "jersey-core"                       % Version.jersey
  val jerseyClient      = "com.sun.jersey"    %  "jersey-client"                     % Version.jersey
  val logbackClassic    = "ch.qos.logback"    %  "logback-classic"                   % Version.logback
  val rdf4jRuntime      = "org.eclipse.rdf4j" % "rdf4j-runtime"                      % Version.rdf4j withSources()
  val scalaTest         = "org.scalatest"     %% "scalatest"                         % Version.scalaTest   % "it,test"
  val akkaTestkit       = "com.typesafe.akka" %% "akka-testkit"                      % Version.akka        % "it,test"
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit"               % Version.akka        % "it,test"
  val fusekiServer      = "org.apache.jena"   %  "jena-fuseki-server"                % Version.fuseki      % "it,test"
}

object Build extends sbt.Build {

  import BuildSettings._
  import PublishingSettings._
  import Library._
  import plugins._

  val projectDependencies = Seq(
    akkaActor, akkaStream, akkaHttpCore, akkaHttpSprayJson, akkaSlf4j,
    javaxWsRs, jerseyCore, jerseyClient, rdf4jRuntime,
    logbackClassic, scalaTest, akkaTestkit, akkaStreamTestkit, fusekiServer)

  lazy val project = Project("reactive-sparql", file("."))
    .configs(IntegrationTest)
    .settings(buildSettings: _*)
    .settings(jenkinsMavenSettings: _*)
    .settings(name := "reactive-sparql")
    .settings(libraryDependencies ++= projectDependencies)
    .settings(coverageMinimum := 65.0D)
} 


