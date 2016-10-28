import NativePackagerHelper._

name := "vscode-scala"
description := "Scala Language Server"
version := "0.0.1"
organization := "net.flatmap"
licenses += "MIT" -> url("https://opensource.org/licenses/MIT")
resolvers += Resolver.bintrayRepo("flatmap", "maven")
libraryDependencies += "net.flatmap" %% "vscode-languageserver" % "0.4.15"
libraryDependencies += "org.ensime" %% "ensime" % "1.0.0"
scalaVersion := "2.11.8"

stagingDirectory in Universal := baseDirectory.value / ".." / "client" / "server"

mappings in Universal ++= {
  contentOf("src/main/resources").map {
    case (k,v) => (k.asFile, "config/" + v)
  }
}

scriptClasspath := Seq("../config/") ++ scriptClasspath.value

enablePlugins(JavaServerAppPackaging)