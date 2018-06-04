import sbt.Keys._
import sbt.{AutoPlugin, _}

import scala.util.Try

object Publishing extends AutoPlugin {

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
