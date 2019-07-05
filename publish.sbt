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
  <url>https://github.com/agnos-ai/reactive-sparql</url>
  <licenses>
    <license>
      <name>MIT License</name>
      <url>http://www.opensource.org/licenses/mit-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/agnos-ai/reactive-sparql.git</url>
    <connection>scm:git:https://github.com/agnos-ai/reactive-sparql.git</connection>
  </scm>
  <developers>
    <developer>
      <id>jgeluk</id>
      <name>Jacobus Geluk</name>
      <url>https://github.com/orgs/agnos-ai/people/jgeluk</url>
    </developer>
    <developer>
      <id>szaniszlo</id>
      <name>Stefan Szaniszlo</name>
      <url>https://github.com/orgs/briskware/people/szaniszlo</url>
    </developer>
  </developers>
}

pgpReadOnly := true
