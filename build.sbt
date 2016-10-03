

lazy val commonSettings = Seq(
  version := "0.01",
  name := "pdf-akka-multi",
  organization := "io.github.qwefgh90",
  scalaVersion := "2.11.7",
  test in assembly := {}
)

lazy val playApp = (project in file("playApp")).enablePlugins(PlayScala).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("play.core.server.ProdServerStart"),
	fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value),
	assemblyMergeStrategy in assembly := {
	  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
	  case PathList("META-INF", xs @ _*) => MergeStrategy.last
	  case PathList("org", "apache", "commons", "logging", xs @ _*) => MergeStrategy.last
	  case x =>
		val oldStrategy = (assemblyMergeStrategy in assembly).value
		oldStrategy(x)
	}
  ).
  dependsOn(pdfActor)

lazy val pdfActor = (project in file("pdfActor")).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("io.github.qwefgh90.akka.pdf.PdfWorker")
  )
  

