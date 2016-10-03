name := """PdfActor"""

version := "0.01"

val akkaVersion = "2.4.4"
scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
       "org.apache.pdfbox" % "pdfbox" % "2.0.2",
      "org.apache.pdfbox" % "xmpbox" % "2.0.2",
      "org.scalatest" % "scalatest_2.11" % "2.2.6" % "test",
      "org.scalactic" % "scalactic_2.11" % "2.2.6",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % "2.2.1" % "test",
      "io.kamon" % "sigar-loader" % "1.6.6-rev002"
  )
  