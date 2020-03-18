name := "distributedsystems17"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies += "com.typesafe.akka" %% "akka-stream-typed" % "2.6.3"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.1.11"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.11"
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime
libraryDependencies += "org.scalactic" %% "scalactic" % "3.1.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % "test"
