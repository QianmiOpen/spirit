import com.github.retronym.SbtOneJar._

oneJarSettings

name := "spirit"

organization := "com.qianmi.bugatti"

version := "1.3.1"
//version := "1.1-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= {
  val sprayV = "1.2.1"
  val akkaV = "2.2.2"
  Seq(
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "io.spray" % "spray-can" % sprayV,
    "com.typesafe.play" %% "play-json" % "2.2.3",
    "ch.qos.logback" % "logback-classic" % "1.1.2"
//    "io.spray" % "spray-routing" % sprayV
  )
}

mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".conf")) }

scalacOptions in ThisBuild ++= Seq("-feature")