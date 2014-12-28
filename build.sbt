name := """pwguard"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

 scalaVersion := "2.11.1"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "com.typesafe.slick"   %%  "slick"                      % "2.1.0",
  "org.xerial"            %  "sqlite-jdbc"                % "3.7.2",
  "org.clapper"          %%  "grizzled-scala"             % "1.3",
  "org.mindrot"           %  "jbcrypt"                    % "0.3m",
  "joda-time"             %  "joda-time"                  % "2.3",
  "org.apache.commons"    %  "commons-math3"              % "3.3",
  "net.sf.uadetector"     %  "uadetector-resources"       % "2014.04",
  "com.google.guava"      %  "guava"                      % "17.0",
  "com.github.tototoshi" %%  "scala-csv"                  % "1.1.2",
  "org.webjars"           %  "bootstrap"                  % "3.0.0",
  "org.webjars"           %  "angularjs"                  % "1.3.8",
  "org.webjars"           %  "angular-route-segment"      % "1.3.3",
  "org.webjars"           %  "font-awesome"               % "4.2.0",
  "org.webjars"           %  "jquery"                     % "1.11.2",
  "org.webjars"           %  "jquery-ui-themes"           % "1.11.0",
  "org.webjars"           %  "log4javascript"             % "1.4.10",
  "org.webjars"           %  "angular-datatables"         % "0.3.0",
  "org.webjars"           %  "underscorejs"               % "1.7.0-1",
  "org.webjars"           %  "angular-strap"              % "2.1.4",
  "org.webjars"           %  "nervgh-angular-file-upload" % "1.1.5-1"
)


includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"
