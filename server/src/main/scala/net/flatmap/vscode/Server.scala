package net.flatmap.vscode

import java.io.{OutputStream, PrintStream}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import io.circe.{Encoder, Json}
import net.flatmap.jsonrpc._
import net.flatmap.vscode.languageserver._

object Server extends App {
	val stdout = System.out
	// We want to redirect all printlns etc away from stdout
	System.setOut(new PrintStream(new OutputStream {
		def write(b: Int): Unit = ()
	}))

	implicit val system = ActorSystem("vscode-scala", com.typesafe.config.ConfigFactory.parseResources("application.conf"))
	implicit val materializer = ActorMaterializer()
	implicit val dispatcher = system.dispatcher

	type Capabilities = LanguageServer
		with ServerCapabilities.CompletionProvider

	def openConnection(constructor: (LanguageClient with RemoteConnection) => Capabilities):
	Connection[Capabilities,LanguageClient with RemoteConnection] = {
		import net.flatmap.vscode.languageserver.Codec._

		implicit val encodeDiagnostics = Encoder.instance[Seq[Diagnostic]] { diags =>
			Json.arr(diags.map(encodeDiagnostic.apply) :_*)
		}

		val client = Remote[LanguageClient](Id.standard)
		val server = Local[Capabilities]

		val connection = Connection.bidi(server, client, constructor)

		val in = StreamConverters.fromInputStream(() => System.in)
		val out = StreamConverters.fromOutputStream(() => stdout)

		in.viaMat(connection)(Keep.right).to(out).run()
	}

	val connection = openConnection(client => new ScalaServer(client))
}