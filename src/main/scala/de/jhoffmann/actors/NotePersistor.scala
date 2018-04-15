package de.jhoffmann.actors

import akka.actor.{Actor, ActorLogging, Props}
import com.evernote.edam.`type`.Note
import com.google.common.util.concurrent.RateLimiter
import de.jhoffmann.actors.NotePersistor.Persist
import de.jhoffmann.actors.ProgressReporter.Increment
import de.jhoffmann.api.Evernote

object NotePersistor {
  def props(rate: Double, ev: Evernote, notebookGuid: String): Props = Props(new NotePersistor(rate, notebookGuid, ev))

  case class Persist(note: Note)
}

class NotePersistor(rate: Double, notebookGuid: String, ev: Evernote) extends Actor with ActorLogging {

  private val throttle         = RateLimiter.create(rate)
  private val progressReporter = context.actorSelection("/user/progressReporter")

  override def receive: Receive = {
    case Persist(note) =>
      throttle.acquire
      ev.persistNote(note, notebookGuid)
      progressReporter ! Increment
      log.debug(s"Persisted note '${note.getTitle}'")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    message match {
      case Some(Persist(note)) =>
        if (note.isSetAttributes) log.warning(s"Could not persist note for ${note.getAttributes.getSourceURL}")
        else log.warning(s"Could not persist note '${note.getTitle}'")

      case _ =>
    }
    super.preRestart(reason, message)
  }
}
