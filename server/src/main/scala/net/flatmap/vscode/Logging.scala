package net.flatmap.vscode

import java.io.{ByteArrayOutputStream, File, FileWriter, PrintStream}
import java.nio.charset.StandardCharsets
import java.sql.Timestamp

import akka.actor.Actor
import akka.dispatch.RequiresMessageQueue
import akka.event.Logging._
import akka.event.{LoggerMessageQueueSemantics, Logging, LoggingAdapter}
import net.flatmap.vscode.languageserver.{LanguageClient, MessageType}

import scala.concurrent.Promise

// FIXME: This global state thing here is ugly
object VSCodeLogger {
  var instance = Promise[LanguageClient]
}

class VSCodeLogger extends Actor with RequiresMessageQueue[LoggerMessageQueueSemantics] {
  implicit val ec = context.dispatcher

  override def receive: Receive = {
    case InitializeLogger(_) =>
      sender ! LoggerInitialized
    case Error(cause,logSource,logClass,message) =>
      VSCodeLogger.instance.future.foreach { client =>
        val msg = if (message == null) cause.getMessage else message.toString
        val bs = new ByteArrayOutputStream()
        val st = new PrintStream(bs)
        cause.printStackTrace(st)
        st.close()
        bs.close()
        val s = bs.toString()
        client.window.logMessage(MessageType.Error,
          s"[error] [$logSource] $msg\n$s"
        )
      }
    case Warning(logSource,logClass,message) =>
      VSCodeLogger.instance.future.foreach { client =>
        client.window.logMessage(MessageType.Warning,
          s"[warn] [$logSource] $message"
        )
      }
    case Info(logSource, logClass, message) =>
      VSCodeLogger.instance.future.foreach { client =>
        client.window.logMessage(MessageType.Warning,
          s"[info] [$logSource] $message"
        )
      }
    case Debug(logSource, logClass, message) =>
      VSCodeLogger.instance.future.foreach { client =>
        client.window.logMessage(MessageType.Warning,
          s"[debug] [$logSource] $message"
        )
      }
  }
}