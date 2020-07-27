import sbt._

object Dependencies {

  object Versions {
    val akka = "2.6.5"
    val akkaHttp = "10.1.11"
  }

  lazy val allDeps = Seq(
    "org.scalatest" %% "scalatest" % "3.1.1" % Test,
    "org.mockito" % "mockito-core" % "3.3.0" % Test,
    "org.mockito" %% "mockito-scala-scalatest" % "1.11.4" % Test,
    "org.slf4j" % "slf4j-log4j12" % "1.7.30",
    "org.apache.logging.log4j" % "log4j-core" % "2.13.0",
    "joda-time" % "joda-time" % "2.10.6"
  )

  lazy val akkaClassic = Seq(
    "com.typesafe.akka" %% "akka-actor" % Versions.akka,
    "com.typesafe.akka" %% "akka-testkit" % Versions.akka % Test,
    "com.typesafe.akka" %% "akka-stream" % Versions.akka,
    "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
    "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHttp,
    "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttp % Test
  )
}
