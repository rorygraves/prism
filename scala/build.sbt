name := "prism-scala"
version := "0.1.0"
scalaVersion := "3.3.1"

lazy val commonSettings = Seq(
  organization := "com.prism",
  scalaVersion := "3.3.1",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "ujson" % "3.1.3",
    "org.scalatest" %% "scalatest" % "3.2.17" % Test
  )
)

// Core library
lazy val core = (project in file("core"))
  .settings(
    commonSettings,
    name := "prism-core",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "ujson" % "3.1.3",
      "io.github.cquiroz" %% "scala-java-time" % "2.5.0"
    )
  )

// Server components
lazy val server = (project in file("server"))
  .settings(
    commonSettings,
    name := "prism-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "com.typesafe.slick" %% "slick" % "3.4.1",
      "org.postgresql" % "postgresql" % "42.6.0"
    )
  )
  .dependsOn(core)

// Play Framework example
lazy val playExample = (project in file("examples/play"))
  .settings(
    commonSettings,
    name := "prism-play-example",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % "2.9.0",
      "com.typesafe.play" %% "play-akka-http-server" % "2.9.0",
      "com.typesafe.play" %% "play-json" % "2.10.3"
    )
  )
  .dependsOn(core, server)
  .enablePlugins(PlayScala)

// Akka HTTP example
lazy val akkaHttpExample = (project in file("examples/akka-http"))
  .settings(
    commonSettings,
    name := "prism-akka-http-example",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5"
    )
  )
  .dependsOn(core, server)

// Root project
lazy val root = (project in file("."))
  .aggregate(core, server, playExample, akkaHttpExample)
  .settings(
    name := "prism-scala"
  )
