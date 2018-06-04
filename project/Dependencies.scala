import sbt._


object Version {

  val scala      = "2.12.6"
  val akka       = "2.5.12"
  val akkaHttp   = "10.1.0"
  val javaxWsRs  = "1.1.1"
  //val jersey     = "1.19.4"
  val rdf4j      = "2.3.2"
  val logback    = "1.2.3"
  val scalaTest  = "3.0.5"
  val fuseki     = "3.7.0"
}


object Dependencies {

  val akkaActor         = "com.typesafe.akka" %% "akka-actor"                        % Version.akka
  val akkaStream        = "com.typesafe.akka" %% "akka-stream"                       % Version.akka
  val akkaHttpCore      = "com.typesafe.akka" %% "akka-http-core"                    % Version.akkaHttp
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json"              % Version.akkaHttp
  val akkaSlf4j         = "com.typesafe.akka" %% "akka-slf4j"                        % Version.akka
  val javaxWsRs         = "javax.ws.rs"       %  "jsr311-api"                        % Version.javaxWsRs
  //val jerseyCore        = "com.sun.jersey"    %  "jersey-core"                       % Version.jersey
  //val jerseyClient      = "com.sun.jersey"    %  "jersey-client"                     % Version.jersey
  val logbackClassic    = "ch.qos.logback"    %  "logback-classic"                   % Version.logback
  val rdf4jRuntime      = "org.eclipse.rdf4j" %  "rdf4j-runtime"                     % Version.rdf4j
  val scalaTest         = "org.scalatest"     %% "scalatest"                         % Version.scalaTest   % "it,test"
  val akkaTestkit       = "com.typesafe.akka" %% "akka-testkit"                      % Version.akka        % "it,test"
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit"               % Version.akka        % "it,test"
  val fusekiServer      = "org.apache.jena"   %  "jena-fuseki-server"                % Version.fuseki      % "it,test"

  val `reactive-sparql-dependencies` = Seq(
    akkaActor, akkaStream, akkaHttpCore, akkaHttpSprayJson, akkaSlf4j,
    javaxWsRs, /*jerseyCore, jerseyClient, */ rdf4jRuntime,
    logbackClassic, scalaTest, akkaTestkit, akkaStreamTestkit, fusekiServer)
}
