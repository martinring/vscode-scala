package net.flatmap.vscode

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import io.circe.Json
import net.flatmap.jsonrpc._
import net.flatmap.vscode.languageserver._

import scala.collection.GenTraversable
import scala.concurrent.{ExecutionContext, Future, Promise}

class Server(client: LanguageClient)(implicit ec: ExecutionContext) extends LanguageServer with CompletionProvider {
	override def textDocumentSyncKind: TextDocumentSyncKind =
		TextDocumentSyncKind.None

	def initialize(processId: Option[Int],
	               rootPath: Option[String],
	               initializationOptions: Option[Json],
	               capabilities: ClientCapabilities): Future[InitializeResult] =
		Future.successful(InitializeResult(this.capabilities))

	def shutdown(): Future[Unit] = Future.successful()
	def exit(): Unit = sys.exit()

	val textDocuments = collection.mutable.Map.empty[URI,TextDocumentItem]

	var maxNumberOfProblems = 100

	def validateTextDocument(textDocument: TextDocumentItem): Unit = {
		val diags = textDocument.text.lines.toSeq.zipWithIndex.flatMap { case (line,n) =>
			val index = line.indexOf("typescript")
			if (index >= 0) Seq[Diagnostic](
				Diagnostic(
					range = Range(n,index,n,index + 10),
					severity = Some(DiagnosticSeverity.Warning),
					code = None,
					message = s"${line.substring(index,10)} should be spelled TypeScript",
					source = Some("ex")
				)
			)
		  else Seq.empty[Diagnostic]
		}
		client.textDocument.publishDiagnostics(textDocument.uri, diags)
	}

	def didOpen(textDocument: TextDocumentItem): Unit = {
		textDocuments += textDocument.uri -> textDocument
		validateTextDocument(textDocument)
	}

	def didChange(textDocument: VersionedTextDocumentIdentifier, contentChanges: Seq[TextDocumentContentChangeEvent]): Unit = {
		for {
			old  <- textDocuments.get(textDocument.uri)
			last <- contentChanges.lastOption
		} {
			textDocuments(textDocument.uri) = old.copy(
				text = last.text,
				version = textDocument.version
			)
			client.window.logMessage(MessageType.Info,s"document changed: ${textDocument.uri}@${textDocument.version}\n${last.text}")
			validateTextDocument(textDocuments(textDocument.uri))
		}
	}

	def didClose(textDocument: TextDocumentIdentifier): Unit = {
		textDocuments -= textDocument.uri
	}

	def didSave(textDocument: TextDocumentIdentifier): Unit = {
		client.window.logMessage(MessageType.Log, s"${textDocument.uri} got saved")
	}

	def didChangeWatchedFiles(changes: Seq[FileEvent]): Unit = {
		// Monitored files have changed in VSCode
		client.window.logMessage(MessageType.Log, "We received a file change event")
	}

	override def completionOptions: CompletionOptions =
		CompletionOptions(resolveProvider = Some(true))

	def completion(textDocument: TextDocumentIdentifier,
	               position: Position): Future[CompletionList] = Future.successful {
		CompletionList(Seq(
			CompletionItem(
				label = "TypeScript",
				kind = Some(CompletionItemKind.Text),
				data = Some(Json.fromInt(1))
			),
			CompletionItem(
				label = "JavaScript",
				kind = Some(CompletionItemKind.Text),
				data = Some(Json.fromInt(2))
			)
		))
	}

	def resolveCompletionItem(item: CompletionItem): Future[CompletionItem] = Future.successful {
		if (item.data == Some(Json.fromInt(1)))
			item.copy(
				detail = Some("TypeScript details"),
				documentation = Some("TypeScript documentation"))
		else if (item.data == Some(Json.fromInt(2)))
			item.copy(
				detail = Some("TypeScript details"),
				documentation = Some("TypeScript documentation"))
		else item
	}

	def didChangeConfiguration(settings: Map[String, Json]): Unit = {
		val maxNumberOfProblems = for {
			languageServerExample <- settings.get("languageServerExample")
			languageServerExample <- languageServerExample.asObject
			maxNumberOfProblems <- languageServerExample.toMap.get("maxNumberOfProblems")
			maxNumberOfProblems <- maxNumberOfProblems.asNumber
		} yield maxNumberOfProblems.toInt.getOrElse(100)
		this.maxNumberOfProblems = maxNumberOfProblems getOrElse (100)
	}
}

object Server extends App {
	implicit val system = ActorSystem()
	implicit val materializer = ActorMaterializer()
	implicit val dispatcher = system.dispatcher

	import net.flatmap.vscode.languageserver.Codec._

	type Type = LanguageServer with CompletionProvider

	val client = Remote[LanguageClient](Id.standard)
	val server = Local[Type]

	val connection = Connection.bidi(server,client,(client: LanguageClient) => new Server(client))

	val in = StreamConverters.fromInputStream(() => System.in)
	val out = StreamConverters.fromOutputStream(() => System.out)

	val socket = in.viaMat(connection)(Keep.right).to(out).run()
}