package net.flatmap.vscode

import java.io.{FileOutputStream, PrintStream}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import net.flatmap.jsonrpc._
import net.flatmap.vscode.languageserver._

object Server extends LanguageServerApp {
	override type Capabilities = LanguageServer
		with ServerCapabilities.CompletionProvider

	val connection = openConnection(client => new ScalaServer(client))
}