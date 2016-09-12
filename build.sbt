organization := "rtfpessoa"
name := "coursier-s3"
version := "1.0.0-alpha.1"

scalaVersion := "2.10.6"

crossScalaVersions := Seq("2.10.6", "2.11.8")

libraryDependencies ++= Seq(
  "com.github.seratch" %% "awscala" % "0.5.+",
  "com.lihaoyi" %% "utest" % "0.4.3" % "test"
)

testFrameworks += new TestFramework("utest.runner.Framework")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
bintrayPackageLabels := Seq("coursier", "sbt", "s3", "aws")
bintrayReleaseOnPublish in ThisBuild := false
