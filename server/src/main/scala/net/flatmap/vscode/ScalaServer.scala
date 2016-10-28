package net.flatmap.vscode

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ErrorMessages
import io.circe.Json
import net.flatmap.jsonrpc.{ErrorCodes, ResponseError}
import net.flatmap.vscode.languageserver._
import org.ensime.api.{TextEdit => _, _}
import org.ensime.config.EnsimeConfigProtocol
import org.ensime.core.{Broadcaster, Project}

import scala.concurrent._
import scala.util.Try

class ScalaServer(client: LanguageClient)(implicit ec: ExecutionContext, mat: Materializer, system: ActorSystem) extends
  LanguageServer
  with ServerCapabilities.CompletionProvider
  with TextDocuments
  with Configuration[Config] {

  var maxNumberOfProblems = 100

  def initialize(processId: Option[Int],
                 rootPath: Option[String],
                 initializationOptions: Option[Json],
                 capabilities: ClientCapabilities,
                 trace: Option[Trace]): Future[InitializeResult] = {
    val res = Try {
      EnsimeConfigProtocol.parse(Files.toString(new File(rootPath.getOrElse(".") + "/.ensime"), Charsets.UTF_8))
    }.map { implicit config =>
      val broadcaster = system.actorOf(Broadcaster(), "broadcaster")
      broadcaster ! Broadcaster.Register
      val msgs = Source.actorRef[EnsimeServerMessage](1024,OverflowStrategy.fail)
      val handler = Sink.foreach[EnsimeServerMessage] {
        case msg => client.window.logMessage(MessageType.Log, msg.toString)
      }
      val ref = msgs.to(handler).run()
      broadcaster.tell(Broadcaster.Register,ref)
      val project = system.actorOf(Project(broadcaster), "project")
      project.tell(ConnectionInfoReq,ref)
      textDocuments.runForeach {
        case (uri,src) =>
          src.runForeach(validateTextDocument)
      }
      client.window.logMessage(MessageType.Log, "initialized ensime")
    }
    res.recover {
      case t =>
        client.window.showMessage(MessageType.Error,t.getMessage)
        ResponseError(
          ErrorCodes.serverErrorStart,
          t.getMessage,
          Some(Codec.encodeInitializeError(InitializeError(retry = false)))
        )
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

  @net.flatmap.jsonrpc.JsonRPC.Named("$/setTraceNotification")
  def setTrace(trace: Trace): Unit = ()

  @net.flatmap.jsonrpc.JsonRPC.Named("textDocument/willSave")
  def willSaveDocument(textDocument: TextDocumentIdentifier, reason: TextDocumentSaveReason): Unit = ()

  @net.flatmap.jsonrpc.JsonRPC.Named("textDocument/willSaveWaitUntil")
  def willSaveDocumentWaitUntil(textDocument: TextDocumentIdentifier, reason: TextDocumentSaveReason): Future[Seq[TextEdit]] = Future.successful(Seq.empty)
}