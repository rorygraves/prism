// Prism Scala Backend - Multi-Project Build
// Cross-compiles for Scala 2.13 and Scala 3

ThisBuild / organization := "com.prism"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / crossScalaVersions := Seq("2.13.12", "3.3.1")

// Publishing configuration for Maven Central
ThisBuild / homepage := Some(url("https://github.com/rorygraves/prism"))
ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/rorygraves/prism"),
    "scm:git@github.com:rorygraves/prism.git"
  )
)
ThisBuild / developers := List(
  Developer(
    "rorygraves",
    "Rory Graves",
    "rory@example.com",
    url("https://github.com/rorygraves")
  )
)

// Publishing settings
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

// Compiler options
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "utf8",
  "-deprecation",
  "-feature",
  "-unchecked"
)

// Scala 2.13 specific options
ThisBuild / scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 13)) => Seq(
      "-Xfatal-warnings",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )
    case Some((3, _)) => Seq(
      "-Xfatal-warnings",
      "-Wunused:all"
    )
    case _ => Seq.empty
  }
}

// Dependency versions
lazy val ujsonVersion = "3.1.4"
lazy val upickleVersion = "3.1.4"
lazy val catsEffectVersion = "3.5.2"
lazy val fs2Version = "3.9.3"
lazy val http4sVersion = "0.23.24"
lazy val playVersion = "2.9.0"
lazy val doobieVersion = "1.0.0-RC4"
lazy val munitVersion = "0.7.29"
lazy val scalaCheckVersion = "1.17.0"
lazy val logbackVersion = "1.4.14"

// Common test dependencies
lazy val testDependencies = Seq(
  "org.scalameta" %% "munit" % munitVersion % Test,
  "org.scalameta" %% "munit-scalacheck" % munitVersion % Test,
  "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test
)

// JSON dependencies using ujson/upickle
lazy val ujsonDependencies = Seq(
  "com.lihaoyi" %% "ujson" % ujsonVersion,
  "com.lihaoyi" %% "upickle" % upickleVersion
)

// Root project - aggregates all sub-projects
lazy val root = (project in file("."))
  .aggregate(
    prismCore,
    prismPlayDemo,
    prismHttp4sDemo
  )
  .settings(
    name := "prism-scala",
    publish / skip := true
  )

// Core protocol library - cross-compiled for Scala 2.13 and 3
lazy val prismCore = (project in file("core"))
  .settings(
    name := "prism-core",
    description := "Versioned object synchronization protocol for Scala",
    libraryDependencies ++= testDependencies ++ ujsonDependencies ++ Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "com.lihaoyi" %% "geny" % "1.0.0", // For JSON Patch diffing
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion
    ),
    Test / parallelExecution := false
  )

// Play Framework integration - Scala 2.13 only (Play doesn't support Scala 3 yet)
lazy val prismPlayDemo = (project in file("integrations/play"))
  .dependsOn(prismCore % "compile->compile")
  .enablePlugins(PlayScala)
  .settings(
    name := "prism-play",
    description := "Play Framework integration for Prism protocol",
    scalaVersion := "2.13.12",
    crossScalaVersions := Seq("2.13.12"), // Play 2.9 only supports Scala 2.13
    // Remove -Xfatal-warnings for Play demo (Play template compiler generates unavoidable warnings)
    scalacOptions := scalacOptions.value.filterNot(_ == "-Xfatal-warnings"),
    libraryDependencies ++= Seq(
      guice,
      "com.typesafe.play" %% "play" % playVersion,
      "com.typesafe.play" %% "play-json" % "2.10.3",
      ws,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.scalameta" %% "munit-scalacheck" % munitVersion % Test,
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test
    ),
    publish / skip := true
  )

// http4s integration - cross-compiled
lazy val prismHttp4sDemo = (project in file("integrations/http4s"))
  .dependsOn(prismCore % "compile->compile")
  .settings(
    name := "prism-http4s",
    description := "http4s integration for Prism protocol",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "co.fs2" %% "fs2-core" % fs2Version,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.scalameta" %% "munit-scalacheck" % munitVersion % Test,
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test
    ),
    publish / skip := true,
    assembly / assemblyJarName := "prism-http4s-demo.jar",
    assembly / mainClass := Some("prism.demo.http4s.Server")
  )

// Test settings
Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b")

// Assembly settings to create fat JARs
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.first
  case PathList("META-INF", xs @ _*) =>
    xs match {
      case "MANIFEST.MF" :: Nil => MergeStrategy.discard
      case "services" :: _ => MergeStrategy.concat
      case _ => MergeStrategy.discard
    }
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}
