resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers ++= Seq("mdedetrich-releases" at "http://artifactory.mdedetrich.com/plugins-release")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.7")

// web plugins

//addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-mocha" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-traceur" % "1.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "1.0.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

// git-stamp: Add Git commit information to generated MANIFEST.MF
addSbtPlugin("com.atlassian.labs" % "sbt-git-stamp" % "0.1.2")
