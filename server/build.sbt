import NativePackagerHelper._
import org.ensime.EnsimePlugin.JdkDir

name := "vscode-scala"
description := "Scala Language Server"
version := "0.0.1"
organization := "net.flatmap"
licenses += "MIT" -> url("https://opensource.org/licenses/MIT")
resolvers += Resolver.bintrayRepo("flatmap", "maven")
libraryDependencies += "net.flatmap" %% "vscode-languageserver" % "0.4.17"
libraryDependencies += "org.ensime" %% "core" % "1.0.0"
scalaVersion := "2.11.8"

lazy val JavaTools: File = JdkDir / "lib/tools.jar"

unmanagedJars in Compile += JavaTools

stagingDirectory in Universal := baseDirectory.value / ".." / "client" / "server"

mappings in Universal ++= {
  contentOf("src/main/resources").map {
    case (k,v) => (k.asFile, "config/" + v)
  }
}

scriptClasspath := Seq("../config/") ++ scriptClasspath.value

enablePlugins(JavaServerAppPackaging)