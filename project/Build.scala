import sbt._
import Keys._
import spray.revolver.RevolverPlugin._


object BuildSettings {
  val buildOrganization = "com.modelfabric"
  val buildScalaVersion = Version.scala
  val buildExportJars   = true

  val buildSettings = Seq (
    organization  := buildOrganization,
    scalaVersion  := buildScalaVersion,
    exportJars    := buildExportJars,
    shellPrompt := { state => "sbt [%s]> ".format(Project.extract(state).currentProject.id) },
    scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-language:implicitConversions", "-language:postfixOps", "-Xlint", "-Xfatal-warnings"),
    incOptions    := incOptions.value.withNameHashing(nameHashing     = true),
    ivyScala      := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    parallelExecution in Test := false
  ) ++ Defaults.itSettings ++ Revolver.settings
}

object Version {

  val scala      = "2.11.8"
  val scalaUtils = "0.1-SNAPSHOT"
  val akka       = "2.4.8"
  val sprayJson  = "1.3.2"
  val spray      = "1.3.3"
  val javaxWsRs  = "1.1.1"
  val jersey     = "1.19"
  val logback    = "1.1.4"
  val scalaTest  = "2.2.5"
  val fuseki     = "2.4.0"
}

object Library {

  val scalaUtils        = "com.modelfabric"   %% "scala-utils"                       % Version.scalaUtils
  val akkaActor         = "com.typesafe.akka" %% "akka-actor"                        % Version.akka
  val akkaStream        = "com.typesafe.akka" %% "akka-stream"                       % Version.akka
  val akkaHttpCore      = "com.typesafe.akka" %% "akka-http-core"                    % Version.akka
  val akkaHttpExp       = "com.typesafe.akka" %% "akka-http-experimental"            % Version.akka
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % Version.akka
  val akkaSlf4j         = "com.typesafe.akka" %% "akka-slf4j"                        % Version.akka
  val sprayJson         = "io.spray"          %  "spray-json_2.11"                   % Version.sprayJson
  val sprayClient       = "io.spray"          %% "spray-client"                      % Version.spray
  val javaxWsRs         = "javax.ws.rs"       %  "jsr311-api"                        % Version.javaxWsRs
  val jerseyCore        = "com.sun.jersey"    %  "jersey-core"                       % Version.jersey
  val jerseyClient      = "com.sun.jersey"    %  "jersey-client"                     % Version.jersey
  val logbackClassic    = "ch.qos.logback"    %  "logback-classic"                   % Version.logback
  val scalaTest         = "org.scalatest"     %% "scalatest"                         % Version.scalaTest   % "it,test"
  val akkaTestkit       = "com.typesafe.akka" %% "akka-testkit"                      % Version.akka        % "it,test"
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit"               % Version.akka        % "it,test"
  val fusekiServer      = "org.apache.jena"   %  "jena-fuseki-server"                % Version.fuseki      % "it,test"
}

object Build extends sbt.Build {

  import BuildSettings._
  import Library._
  import plugins._

  val projectDependencies = Seq(
    scalaUtils, akkaActor, akkaStream, akkaHttpCore, akkaHttpExp, akkaHttpSprayJson, akkaSlf4j,
    sprayClient, sprayJson, javaxWsRs, jerseyCore, jerseyClient,
    logbackClassic, scalaTest, akkaTestkit, akkaStreamTestkit, fusekiServer)

  lazy val project = Project("reactive-sparql", file("."))
    .configs(IntegrationTest)
    .settings(buildSettings: _*)
    .settings(name := "reactive-sparql")
    .settings(libraryDependencies ++= projectDependencies)
} 


