name := "sbt-avro"
organization := "me.andreionut"
description := "Sbt plugin for compiling Avro sources"
version := "1.1.1"

scalaVersion := "2.10.6"

sbtPlugin := true

libraryDependencies ++= Seq(
  "org.apache.avro" % "avro" % "1.7.7",
  "org.apache.avro" % "avro-compiler" % "1.7.7",
  "com.assembla.scala-incubator" % "graph-core_2.10" % "1.9.1",
  "com.jayway.jsonpath" % "json-path" % "2.0.0",
  "org.specs2" %% "specs2-core" % "3.6.4" % "test"
)

// Publishing Options
bintrayPackageLabels := Seq("sbt", "sbt-plugin", "avro")
//bintrayReleaseOnPublish in ThisBuild := false
bintrayRepository := "sbt-plugins"
publishMavenStyle := false
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publish <<= publish dependsOn (test in Test)

