name := "language-server-example"
description := "Example implementation of a language server in Scala."
version := "0.0.1"
organization := "net.flatmap"
licenses += "MIT" -> url("https://opensource.org/licenses/MIT")
resolvers += Resolver.bintrayRepo("flatmap", "maven")
libraryDependencies += "net.flatmap" %% "vscode-languageserver" % "0.4.12"
scalaVersion := "2.11.8"

stagingDirectory in Universal := baseDirectory.value / ".." / "client" / "server"

enablePlugins(JavaAppPackaging)