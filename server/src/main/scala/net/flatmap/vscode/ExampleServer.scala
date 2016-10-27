package net.flatmap.vscode

import akka.stream.Materializer
import io.circe.Json
import net.flatmap.vscode.languageserver._
import scala.concurrent._

class ExampleServer(client: LanguageClient)(implicit ec: ExecutionContext, mat: Materializer) extends
  LanguageServer
  with ServerCapabilities.CompletionProvider
  with TextDocuments
  with Configuration[Config] {

  var maxNumberOfProblems = 100

  def initialize(processId: Option[Int],
                 rootPath: Option[String],
                 initializationOptions: Option[Json],
                 capabilities: ClientCapabilities): Future[InitializeResult] = {
    textDocuments.runForeach {
      case (uri,src) =>
        src.runForeach(validateTextDocument)
    }
    configChanges.runForeach {
      case Config(Some(ExampleConfig(Some(maxNumberOfProblems)))) =>
        this.maxNumberOfProblems = maxNumberOfProblems
    }
    Future.successful(InitializeResult(this.capabilities))
  }

  def shutdown(): Future[Unit] =
    Future.successful()

  def exit(): Unit =
    sys.exit()

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
    }.take(maxNumberOfProblems)
    client.textDocument.publishDiagnostics(textDocument.uri, diags)
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
}