import scala.util.Try

publishMavenStyle       := true

publishArtifact in Test := false

pomIncludeRepository    := { _ => false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := {
  <scm>
    <url>https://github.org/modelfabric/reactive-sparql.git</url>
    <connection>scm:git:https://github.org/modelfabric/reactive-sparql.git</connection>
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
