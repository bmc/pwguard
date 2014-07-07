name := """pwguard"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

// scalaVersion := "2.11.1"
scalaVersion := "2.10.4"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  //"com.typesafe.slick" %% "slick" % "2.1.0-M2",
  "com.typesafe.slick" %% "slick"                 % "2.0.2",
  "org.xerial"          % "sqlite-jdbc"           % "3.7.2",
  "org.clapper"        %% "grizzled-scala"        % "1.2",
  "org.mindrot"         % "jbcrypt"               % "0.3m",
  "joda-time"           % "joda-time"             % "2.3",
  "org.apache.commons"  % "commons-math3"         % "3.3",
  "net.sf.uadetector"   % "uadetector-resources"  % "2014.04",
  "com.google.guava"    % "guava"                 % "17.0"
)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"
