import build.helpers._

name := """pwguard"""

version := "1.0.2-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

resolvers += "Spy Repository" at "http://files.couchbase.com/maven2"

pipelineStages := Seq(digest, gzip)

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
  "org.scalatestplus"    %% "play"                       % "1.1.0" % "test",
  "org.webjars"           % "bootstrap"                  % "3.2.0",
  "org.webjars"           % "modernizr"                  % "2.8.3",
  "org.webjars"           % "excanvas"                   % "3",
  "org.webjars"           % "html5shiv"                  % "3.7.2",
  "org.webjars"           % "traceur"                    % "0.0.79-1",
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
  "org.webjars"           % "font-awesome"               % "4.3.0",
  "org.webjars"           % "jquery"                     % "1.11.2",
  "org.webjars"           % "log4javascript"             % "1.4.10",
  "org.webjars"           % "lodash"                     % "3.1.0",
  "org.webjars"           % "ng-tags-input"              % "2.1.1",
  "org.webjars"           % "momentjs"                   % "2.9.0",
  "org.webjars"           % "jasmine"                    % "2.1.3" % "test"
)

// Gather some build-related stuff

gitStampSettings

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version)

buildInfoPackage := "buildinfo"

// ----------------------------------------------------------------------------
// Command aliases
// ----------------------------------------------------------------------------

// Override the "dist" command to build a tarball, instead of a zip file.
addCommandAlias("dist", ";clean;compile;universal:package-zip-tarball")

// ----------------------------------------------------------------------------
// Special case task that runs traceur, then translates the results via
// angular-injector. Assumes that Node.js, traceur and angular-injector
// are installed and in the path.
//
// We could use sbt-traceur, but this proves to be more flexible. Among other
// things, it allows easy drop-in of angular-injector, a Node.js command that
// transforms AngularJS dependency injection so that it's minification-friendly.
//
// Plus, this approach serves as a good illustration of a non-trivial inline
// SBT task.
// ----------------------------------------------------------------------------

val traceur = taskKey[Seq[File]]("run traceur")

// The actual task body.
traceur := {
  import java.io.File
  import grizzled.file.util.joinPath
  implicit val log = streams.value.log
  //
  // We'll want some temp files.
  //
  val tmpDir = "tmp"
  def newTempFile(name: String): (String, File) = {
    val path = joinPath(tmpDir, name)
    (path, new File(path))
  }
  val (tempPath1, tempFile1) = newTempFile("pwguard.js")
  val (tempPath2, tempFile2) = newTempFile("_pwguard2.js")
  val tempFiles = Seq(tempFile1, tempFile2)
  //
  // Much of this logic is adapted from
  // https://github.com/sbt/sbt-web#writing-a-source-file-task
  //
  // Get the source directory
  val sourceDir = (sourceDirectory in Assets).value
  //
  // Get the list of Javascript sources
  //
  val sources = sourceDir ** "*.js"
  //
  // Map them to a list of (File -> relative-path-string) pairs
  //
  val mappings = sources pair relativeTo(sourceDir)
  //
  // Map them to the outputs. There's only one output file, but it needs to
  // be a sequence of one (file1 -> file2) pair, where:
  //
  // file1 = the File object pointing to output file we're creating in the
  //         temp directory
  // file2 = the final location of the output file.
  //
  // IO.copy will use this information to copy our generated file to the
  // output directory.
  val outputs = mappings.filter {
    case (file, path) => path.contains("main.js")
  }.
  take(1).
  map {
    case (file, path) => {
      val finalOutputDir = (resourceManaged in Assets).value
      tempFile2 -> finalOutputDir / path.replace("main.js", "pwguard.js")
    }
  }
  //
  // Extract the output path string; we'll need it in the traceur command,
  // below.
  //
  val output = outputs.map(_._2).head
  //
  // Get the source file paths, as a string, to poke into the traceur command.
  // The paths are full. Make them relative (mostly because they'll still work
  // fine, and they'll display better).
  //
  val sourceNames   = sources.getPaths
  val sourcesString = sourceNames map { relativePath(_) } mkString " "
  //
  // Invoke traceur, writing output to the first temp file.
  //
  sh("mkdir -p tmp")
  sh(s"traceur --experimental --source-maps=inline --out $tempPath1 --modules=inline $sourcesString")
  //
  // Invoke angular-injector on the result, writing the output to the second
  // temp file.
  //
  sh(s"angular-injector $tempPath1 $tempPath2")
  //
  // Copy the result to the output directory.
  //
  copyAll(outputs)
  //
  // Delete the temp files.
  //
  rm_f(tempFiles)
  //
  // ...and, the result.
  //
  outputs map (_._2)
}

sourceGenerators in Assets <+= traceur

// ---------------------------------------------------------------------------
// Task to run "bower install"
// ----------------------------------------------------------------------------

val bower = taskKey[Unit]("run 'bower install'")

// Run "bower install"
bower := {
  implicit val log = streams.value.log
  sh("bower install")
}

// ---------------------------------------------------------------------------
// Custom compilation logic
// ----------------------------------------------------------------------------

// Some components aren't available in WebJars, so we use Bower. Note that
// the .bowerrc file ensures that Bower components are installed in the
// same directory as WebJars components, thus ensuring that we can move
// components into WebJars references, when they become available.
//
val customCompile = taskKey[Unit]("custom compilation tasks")

customCompile := {
  bower.value
}

compile in Compile <<= (compile in Compile) dependsOn(customCompile)

// ---------------------------------------------------------------------------
// Asset pipeline configuration
// ----------------------------------------------------------------------------

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

// Use (baseDirectory.value / "static" for a full path, but file("static")
// for a relative one.
//
// See http://stackoverflow.com/a/19568979/53495
mappings in Universal ++= (file("static") ** "*").get map { f =>
  f -> f.getPath
}

//includeFilter in uglify := GlobFilter("main.js")

// ----------------------------------------------------------------------------
// If we *were* using sbt-traceur, here's where we'd configure it.
// ----------------------------------------------------------------------------

//TraceurKeys.sourceFileNames in Assets := Seq("javascripts/main.js")

//TraceurKeys.sourceFileNames in TestAssets := Seq.empty[String]

//TraceurKeys.sourceFileNames in TestAssets := Seq("javascript-tests/main.js")
