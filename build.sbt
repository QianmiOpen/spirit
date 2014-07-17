import com.github.retronym.SbtOneJar._

oneJarSettings

name := "spirit"

organization := "com.qianmi.bugatti"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= {
  val sprayV = "1.3.1"
  Seq(
    "com.typesafe.akka" %% "akka-remote" % "2.3.4",
    "io.spray" % "spray-can" % sprayV,
    "io.spray" % "spray-routing" % sprayV
  )
}