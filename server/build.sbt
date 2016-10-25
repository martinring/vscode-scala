name := "language-server-example"
description := "Example implementation of a language server in Scala."
version := "0.0.1"
organization := "net.flatmap"
licenses += "MIT" -> url("https://opensource.org/licenses/MIT")
resolvers += Resolver.bintrayRepo("flatmap", "maven")
libraryDependencies += "net.flatmap" %% "vscode-languageserver" % "0.3.1"
scalaVersion := "2.11.8"