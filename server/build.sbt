lazy val akkaHttpVersion = "10.0.10"
lazy val akkaVersion    = "2.4.19"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "org.clapper",
      scalaVersion    := "2.12.3"
    )),
    name := "server",
    version := "2.0.0-SNAPSHOT",
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"     %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-stream"          % akkaVersion,
      "com.typesafe.akka"     %% "akka-actor"           % akkaVersion,
      "com.iheart"            %% "ficus"                % "1.4.2",
      "com.blueconic"          % "browscap-java"        % "1.0.4",
      "co.fs2"                %% "fs2-core"             % "0.9.7",
      "co.fs2"                %% "fs2-io"               % "0.9.7",
      "com.github.krasserm"   %% "streamz-converter"    % "0.8.1",
      "org.clapper"           %% "grizzled-scala"       % "4.4.2",
      "org.apache.tika"        % "tika-core"            % "1.16",
      "com.typesafe.akka"     %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "org.scalatest"         %% "scalatest"            % "3.0.1"         % Test
    )
  )

val fatjar = taskKey[Unit]("fatjar")
fatjar := assembly.value

// Override run task, since the server doesn't listen on stdin.
val localRun = taskKey[Unit]("run")
run := localRun.value

localRun := {
  println(
    s"""|${scala.Console.RED}
        |To start the server: re-start /path/to/config
        |To stop the server:  re-stop
        |${scala.Console.RESET}""".stripMargin
  )
}
