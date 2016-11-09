package net.flatmap.vscode

import java.io.File
import java.net.URI

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.google.common.base.Charsets
import com.google.common.io.Files
import io.circe.Json
import net.flatmap.jsonrpc.{ErrorCodes, ResponseError}
import net.flatmap.vscode.languageserver.ServerCapabilities.CompletionProvider
import net.flatmap.vscode.languageserver._
import org.ensime.api.{TextEdit => _, _}
import org.ensime.config.EnsimeConfigProtocol
import org.ensime.core.{Broadcaster, Project}

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try

class ScalaServer(client: LanguageClient)(implicit ec: ExecutionContext, mat: Materializer, system: ActorSystem) extends
  LanguageServer
  with CompletionProvider
  with Configuration[Config] {

  VSCodeLogger.instance.trySuccess(client)

  override def textDocumentSyncKind: TextDocumentSyncKind = TextDocumentSyncKind.None

  val project = Promise[ActorRef]
  val self = Promise[ActorRef]

  val notes = mutable.Buffer.empty[Note]

  val messageHandler = Sink.foreach[EnsimeServerMessage] {
    case ClearAllScalaNotesEvent =>
      notes.clear()
    case NewScalaNotesEvent(full, newNotes) =>
      notes.appendAll(newNotes)
    case FullTypeCheckCompleteEvent =>
      publishDiagnostics()
    case msg =>
      client.window.logMessage(MessageType.Info, "RECEIVED UNHANDLED: " + msg.toString)
  }

  def ensime: ActorRef = project.future.value.map(_.get).getOrElse(system.deadLetters)

  def publishDiagnostics() = {
    notes.foldLeft(Map.empty[URI, Seq[Diagnostic]]) {
      case (notes, note) =>
        val uri = new File(note.file).toURI
        docs.get(uri).fold {
          notes
        } { doc =>
          val old = notes.getOrElse(uri, Seq.empty)
          val diag: Diagnostic = Diagnostic(
            Range(doc.positionAt(note.beg),doc.positionAt(note.end)),
            note.msg,
            Some(note.severity match {
              case NoteError => DiagnosticSeverity.Error
              case NoteWarn  => DiagnosticSeverity.Warning
              case NoteInfo  => DiagnosticSeverity.Information
            })
          )
          notes.updated(uri, old :+ diag)
        }
    }.foreach { case (uri, diags) =>
      client.textDocument.publishDiagnostics(uri, diags)
    }
  }

  val docs = mutable.Map.empty[URI, TextDocumentItem]

  def sourceFileInfo(doc: TextDocument) = SourceFileInfo(new File(doc.uri),Some(doc.text))

  def initialize(processId: Option[Int],
                 rootPath: Option[String],
                 initializationOptions: Option[Json],
                 capabilities: ClientCapabilities,
                 trace: Option[Trace]): Future[InitializeResult] = {
    client.window.logMessage(MessageType.Log, "starting up scala server")
    new File(rootPath.getOrElse(".") + "/.ensime_cache").mkdir()
    val res = Try {
      EnsimeConfigProtocol.parse(Files.toString(new File(rootPath.getOrElse(".") + "/.ensime"), Charsets.UTF_8))
    }.map { implicit config =>
      val broadcaster = system.actorOf(Broadcaster(), "broadcaster")
      val msgs = Source.actorRef[EnsimeServerMessage](1024, OverflowStrategy.fail)
      val ref = msgs.to(messageHandler).run()
      this.self.trySuccess(ref)
      broadcaster.tell(Broadcaster.Register, ref)
      val project = system.actorOf(Project(broadcaster), "project")
      this.project.trySuccess(project)
      project.tell(ConnectionInfoReq, ref)
      client.window.logMessage(MessageType.Log, "initialized ensime")
    }
    res.recover {
      case t =>
        client.window.showMessage(MessageType.Error, t.getMessage)
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

  def didChangeWatchedFiles(changes: Seq[FileEvent]): Unit = {
    // Monitored files have changed in VSCode
    client.window.logMessage(MessageType.Log, "We received a file change event")
  }

  def didOpenDocument(textDocument: TextDocumentItem): Unit = {
    docs += textDocument.uri -> textDocument
    ensime ? TypecheckFileReq(SourceFileInfo(new File(textDocument.uri),Some(textDocument.text)))
  }

  def didChangeDocument(textDocument: VersionedTextDocumentIdentifier,
                                 contentChanges: Seq[TextDocumentContentChangeEvent]): Unit = {
    for {
      state <- docs.get(textDocument.uri)
      last <- contentChanges.lastOption
    } {
      docs(state.uri) = state.copy(
        text = last.text,
        version = textDocument.version
      )
      ensime ? TypecheckFileReq(SourceFileInfo(new File(textDocument.uri),Some(last.text)))
    }
  }

  def didCloseDocument(textDocument: TextDocumentIdentifier): Unit = {
    docs.remove(textDocument.uri)
    ensime ? RemoveFileReq(new File(textDocument.uri))
  }

  def didSaveDocument(textDocument: TextDocumentIdentifier): Unit = {
    // TODO
  }

  override def completionOptions: CompletionOptions =
    CompletionOptions(resolveProvider = true)

  implicit val defaultTimeout = Timeout(5 seconds)
  override def completion(textDocument: TextDocumentIdentifier,
                          position: Position): Future[CompletionList] = (for {
    doc <- docs.get(textDocument.uri)
  } yield for {
    response <- (ensime ? CompletionsReq(sourceFileInfo(doc), doc.offsetAt(position), 100, false, false)).mapTo[CompletionInfoList]
  } yield {
    val completionItems = response.completions.map { item =>
      CompletionItem(item.name)
    }
    CompletionList(completionItems,isIncomplete = true)
  }).getOrElse(Future.failed(ResponseError(ErrorCodes.InvalidRequest, "document is not known")))

  override def resolveCompletionItem(item: CompletionItem): Future[CompletionItem] = Future.successful {
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

  def setTrace(trace: Trace): Unit = ()

  def willSaveDocument(textDocument: TextDocumentIdentifier, reason: TextDocumentSaveReason): Unit = ()

  def willSaveDocumentWaitUntil(textDocument: TextDocumentIdentifier, reason: TextDocumentSaveReason): Future[Seq[TextEdit]] = Future.successful(Seq.empty)
}