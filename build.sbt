name := """pwguard"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

resolvers += "Spy Repository" at "http://files.couchbase.com/maven2"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  filters,
  "com.typesafe.slick"   %% "slick"                      % "2.1.0",
  "org.xerial"            % "sqlite-jdbc"                % "3.7.2",
  "org.clapper"          %% "grizzled-scala"             % "1.3",
  "org.mindrot"           % "jbcrypt"                    % "0.3m",
  "joda-time"             % "joda-time"                  % "2.3",
  "net.sf.uadetector"     % "uadetector-resources"       % "2014.04",
  "com.google.guava"      % "guava"                      % "17.0",
  "com.github.tototoshi" %% "scala-csv"                  % "1.1.2",
  "org.apache.commons"    % "commons-math3"              % "3.3",
  "commons-codec"         % "commons-codec"              % "1.10",
  "org.apache.poi"        % "poi"                        % "3.11",
  "org.apache.poi"        % "poi-ooxml"                  % "3.11",
  "com.github.mumoshu"   %% "play2-memcached"            % "0.6.0",
  "org.webjars"           % "bootstrap"                  % "3.2.0",
  "org.webjars"           % "modernizr"                  % "2.8.3",
  "org.webjars"           % "excanvas"                   % "3",
  "org.webjars"           % "html5shiv"                  % "3.7.2",
  // -------------------------------------------------------------------------
  // AngularJS consistency irritations:
  //
  // - angular-strap (at least through webjars) depends on AngularJS 1.3.0
  //   after version 2.1.3, so we have to wire it to 2.1.2.
  //
  // We can't upgrade to Angular 1.3.8 or better, because some of the plugins
  // don't support it yet.
  "org.webjars"           % "angularjs"                  % "1.2.26",
  "org.webjars"           % "angular-strap"              % "2.1.2",
  // -------------------------------------------------------------------------
  "org.webjars"           % "font-awesome"               % "4.2.0",
  "org.webjars"           % "jquery"                     % "1.11.2",
  "org.webjars"           % "log4javascript"             % "1.4.10",
  "org.webjars"           % "underscorejs"               % "1.7.0-1"
)

// Some components aren't available in WebJars, so we use Bower. Note that
// the .bowerrc file ensures that Bower components are installed in the
// same directory as WebJars components, thus ensuring that we can move
// components into WebJars references, when they become available.
//
// Run "bower install" whenever we do a compile. See
// http://stackoverflow.com/a/17734236/53495
compile in Compile <<= (compile in Compile) map { compile =>
  "bower install".!!
  compile
}

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

