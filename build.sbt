name := """pwguard"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

// scalaVersion := "2.11.1"
scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  //"com.typesafe.slick" %% "slick" % "2.1.0-M2",
  "com.typesafe.slick" %% "slick" % "2.0.2",
  "org.xerial" % "sqlite-jdbc" % "3.7.2",
  "org.clapper" %% "grizzled-scala" % "1.2",
  "org.mindrot" % "jbcrypt" % "0.3m"
)
