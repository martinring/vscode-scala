package net.flatmap.vscode

import java.io.{FileOutputStream, PrintStream}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import net.flatmap.jsonrpc._
import net.flatmap.vscode.languageserver._

object Server extends App {
	val stdout = System.out

	// We want to redirect all printlns etc away from stdout
	System.setOut(new PrintStream(new FileOutputStream("stdout.log")))

	implicit val system = ActorSystem("vscode-scala",com.typesafe.config.ConfigFactory.parseResources("application.conf"))
	implicit val materializer = ActorMaterializer()
	implicit val dispatcher = system.dispatcher

	import net.flatmap.vscode.languageserver.Codec._

  val logging = system.logConfiguration()

	type Type = LanguageServer
		with ServerCapabilities.CompletionProvider

	val client = Remote[LanguageClient](Id.standard)
	val server = Local[Type]

	val connection = Connection.bidi(server,client,
		(client: LanguageClient) => new ScalaServer(client))

	val in = StreamConverters.fromInputStream(() => System.in)
	val out = StreamConverters.fromOutputStream(() => stdout)

	val socket = in.viaMat(connection)(Keep.right).to(out).run()
}