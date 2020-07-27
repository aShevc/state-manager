import Dependencies._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerBaseImage

ThisBuild / scalaVersion := "2.13.0"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "org.aShevc"
ThisBuild / organizationName := "aShevc"

lazy val root = (project in file("."))
  .settings(
    name := "state-manager",
    libraryDependencies ++= (allDeps ++ akkaClassic),
    topLevelDirectory := Some(packageName.value),
    packageName in Universal := packageName.value,
    dockerBaseImage := "openjdk:jre-alpine",
    dockerUpdateLatest := true,
    dockerExposedPorts := Seq(8080),
    fork in run := true
  ).enablePlugins(JavaAppPackaging, AshScriptPlugin, UniversalPlugin, DockerPlugin)



