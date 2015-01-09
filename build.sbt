import com.github.retronym.SbtOneJar._

import com.typesafe.sbt.SbtAspectj.{ Aspectj, aspectjSettings, compiledClasses }

import com.typesafe.sbt.SbtAspectj.AspectjKeys.{ binaries, inputs, lintProperties }

oneJarSettings

name := "spirit"

organization := "com.qianmi.bugatti"

version := "1.4.2"
//version := "1.1-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= {
  val sprayV = "1.2.1"
  val akkaV = "2.2.4"
  val kamonV = "0.2.3"
  Seq(
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "io.spray" % "spray-can" % sprayV,
    "com.typesafe.play" %% "play-json" % "2.2.3",
    "ch.qos.logback" % "logback-classic" % "1.1.2",
//    "io.kamon" %% "kamon-core" % kamonV,
//    "io.kamon" %% "kamon-statsd" % kamonV,
    "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test"
//    "io.spray" % "spray-routing" % sprayV
  )
}

mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".conf")) }

scalacOptions in ThisBuild ++= Seq("-feature")

aspectjSettings

inputs in Aspectj <+= compiledClasses

products in Compile <<= products in Aspectj

products in Runtime <<= products in Compile
