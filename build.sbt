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
  "org.apache.poi"        %  "poi"                        % "3.11",
  "org.apache.poi"        %  "poi-ooxml"                  % "3.11",
  "org.webjars"           %  "bootstrap"                  % "3.2.0",
  // Consistency irritations:
  //
  // - nervgh-angular-file-upload depends on AngularJS 1.2.26
  // - angular-strap (at least through webjars) depends on AngularJS 1.3.0
  //   after version 2.1.3, so we have to wire it to 2.1.2.
  "org.webjars"           %  "angularjs"                  % "1.2.26",
  "org.webjars"           %  "angular-strap"              % "2.1.2",
  "org.webjars"           %  "nervgh-angular-file-upload" % "1.1.5-1",
  //
  "org.webjars"           %  "font-awesome"               % "4.2.0",
  "org.webjars"           %  "jquery"                     % "1.11.2",
  "org.webjars"           %  "jquery-ui-themes"           % "1.11.0",
  "org.webjars"           %  "log4javascript"             % "1.4.10",
  "org.webjars"           %  "angular-datatables"         % "0.3.0",
  "org.webjars"           %  "underscorejs"               % "1.7.0-1"
)


includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

// Use (baseDirectory.value / "static" for a full path, but file("static")
// for a relative one.
//
// See http://stackoverflow.com/a/19568979/53495
mappings in Universal ++= (file("static") ** "*").get map { f =>
  f -> f.getPath
}

// Override the "dist" command to build a tarball, instead of a zip file.
addCommandAlias("dist", "universal:package-zip-tarball")

