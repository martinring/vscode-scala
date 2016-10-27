package net.flatmap.vscode

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl._
import io.circe.Json
import net.flatmap.jsonrpc._
import net.flatmap.vscode.languageserver._

import scala.concurrent._

object Server extends App {
	implicit val system = ActorSystem()
	implicit val materializer = ActorMaterializer()
	implicit val dispatcher = system.dispatcher

	import net.flatmap.vscode.languageserver.Codec._

	type Type = LanguageServer
		with ServerCapabilities.CompletionProvider

	val client = Remote[LanguageClient](Id.standard)
	val server = Local[Type]

	val connection = Connection.bidi(server,client,(client: LanguageClient) => new ScalaServer(client))

	val in = StreamConverters.fromInputStream(() => System.in)
	val out = StreamConverters.fromOutputStream(() => System.out)

	val socket = in.viaMat(connection)(Keep.right).to(out).run()
}