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

	// TODO: this is ugly and cumbersome... should be addressed in scala-jsonrpc
	def resolveCompletionItem(label: String,
	                          kind: Option[CompletionItemKind],
	                          detail: Option[String],
	                          documentation: Option[String],
	                          sortText: Option[String],
	                          filterText: Option[String],
	                          insertText: Option[String],
	                          textEdit: Option[TextEdit],
	                          additionalTextEdits: Option[Seq[TextEdit]],
	                          command: Option[Command],
	                          data: Option[Json]): Future[CompletionItem] = Future.successful {
		if (data == Some(Json.fromInt(1)))
			CompletionItem(
				label = label,
				kind = kind,
				detail = Some("TypeScript details"),
				documentation = Some("TypeScript documentation"),
				sortText = sortText,
				filterText = filterText,
				insertText = insertText,
				textEdit = textEdit,
				additionalTextEdits = additionalTextEdits,
				command = command,
				data = data
			)
		else if (data == Some(Json.fromInt(2)))
			CompletionItem(
				label = label,
				kind = kind,
				detail = Some("TypeScript details"),
				documentation = Some("TypeScript documentation"),
				sortText = sortText,
				filterText = filterText,
				insertText = insertText,
				textEdit = textEdit,
				additionalTextEdits = additionalTextEdits,
				command = command,
				data = data
			)
		else CompletionItem(
			label = label,
			kind = kind,
			detail = detail,
			documentation = documentation,
			sortText = sortText,
			filterText = filterText,
			insertText = insertText,
			textEdit = textEdit,
			additionalTextEdits = additionalTextEdits,
			command = command,
			data = data
		)
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


/**
'use strict';

import {
	IPCMessageReader, IPCMessageWriter,
	createConnection, IConnection, TextDocumentSyncKind,
	TextDocuments, TextDocument, Diagnostic, DiagnosticSeverity,
	InitializeParams, InitializeResult, TextDocumentPositionParams,
	CompletionItem, CompletionItemKind
} from 'vscode-languageserver';

// Create a connection for the server. The connection uses Node's IPC as a transport
let connection: IConnection = createConnection(new IPCMessageReader(process), new IPCMessageWriter(process));

// Create a simple text document manager. The text document manager
// supports full document sync only
let documents: TextDocuments = new TextDocuments();
// Make the text document manager listen on the connection
// for open, change and close text document events
documents.listen(connection);

// After the server has started the client sends an initilize request. The server receives
// in the passed params the rootPath of the workspace plus the client capabilites.
let workspaceRoot: string;
connection.onInitialize((params): InitializeResult => {
	workspaceRoot = params.rootPath;
	return {
		capabilities: {
			// Tell the client that the server works in FULL text document sync mode
			textDocumentSync: documents.syncKind,
			// Tell the client that the server support code complete
			completionProvider: {
				resolveProvider: true
			}
		}
	}
});

// The content of a text document has changed. This event is emitted
// when the text document first opened or when its content has changed.
documents.onDidChangeContent((change) => {
	validateTextDocument(change.document);
});

// The settings interface describe the server relevant settings part
interface Settings {
	languageServerExample: ExampleSettings;
}

// These are the example settings we defined in the client's package.json
// file
interface ExampleSettings {
	maxNumberOfProblems: number;
}

// hold the maxNumberOfProblems setting
let maxNumberOfProblems: number;
// The settings have changed. Is send on server activation
// as well.
connection.onDidChangeConfiguration((change) => {
	let settings = <Settings>change.settings;
	maxNumberOfProblems = settings.languageServerExample.maxNumberOfProblems || 100;
	// Revalidate any open text documents
	documents.all().forEach(validateTextDocument);
});

function validateTextDocument(textDocument: TextDocument): void {
	let diagnostics: Diagnostic[] = [];
	let lines = textDocument.getText().split(/\r?\n/g);
	let problems = 0;
	for (var i = 0; i < lines.length && problems < maxNumberOfProblems; i++) {
		let line = lines[i];
		let index = line.indexOf('typescript');
		if (index >= 0) {
			problems++;
			diagnostics.push({
				severity: DiagnosticSeverity.Warning,
				range: {
					start: { line: i, character: index},
					end: { line: i, character: index + 10 }
				},
				message: `${line.substr(index, 10)} should be spelled TypeScript`,
				source: 'ex'
			});
		}
	}
	// Send the computed diagnostics to VSCode.
	connection.sendDiagnostics({ uri: textDocument.uri, diagnostics });
}

connection.onDidChangeWatchedFiles((change) => {
	// Monitored files have change in VSCode
	connection.console.log('We recevied an file change event');
});


// This handler provides the initial list of the completion items.
connection.onCompletion((textDocumentPosition: TextDocumentPositionParams): CompletionItem[] => {
	// The pass parameter contains the position of the text document in
	// which code complete got requested. For the example we ignore this
	// info and always provide the same completion items.
	return [
		{
			label: 'TypeScript',
			kind: CompletionItemKind.Text,
			data: 1
		},
		{
			label: 'JavaScript',
			kind: CompletionItemKind.Text,
			data: 2
		}
	]
});

// This handler resolve additional information for the item selected in
// the completion list.
connection.onCompletionResolve((item: CompletionItem): CompletionItem => {
	if (item.data === 1) {
		item.detail = 'TypeScript details',
		item.documentation = 'TypeScript documentation'
	} else if (item.data === 2) {
		item.detail = 'JavaScript details',
		item.documentation = 'JavaScript documentation'
	}
	return item;
});

connection.onDidOpenTextDocument((params) => {
	// A text document got opened in VSCode.
	// params.uri uniquely identifies the document. For documents store on disk this is a file URI.
	// params.text the initial full content of the document.
	connection.console.log(`${params.uri} opened.`);
});

connection.onDidChangeTextDocument((params) => {
	// The content of a text document did change in VSCode.
	// params.uri uniquely identifies the document.
	// params.contentChanges describe the content changes to the document.
	connection.console.log(`${params.uri} changed: ${JSON.stringify(params.contentChanges)}`);
});

connection.onDidCloseTextDocument((params) => {
	// A text document got closed in VSCode.
	// params.uri uniquely identifies the document.
	connection.console.log(`${params.uri} closed.`);
});


// Listen on the connection
connection.listen();

**/