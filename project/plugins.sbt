// Comment to get more information during initialization
logLevel := Level.Info

resolvers ++= Seq("spray repo" at "http://repo.spray.io")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.2.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
