package de.jhoffmann

import akka.actor.{Actor, ActorLogging, Props}
import com.evernote.edam.`type`.Note
import com.google.common.util.concurrent.RateLimiter
import de.jhoffmann.NotePersistor.Persist
import de.jhoffmann.ProgressReporter.Increment
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
}
