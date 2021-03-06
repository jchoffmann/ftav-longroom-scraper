package de.jhoffmann.actors

import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import com.evernote.edam.`type`.{Note, NoteAttributes}
import de.jhoffmann.actors.NoteCreator.CreateNote
import de.jhoffmann.actors.NotePersistor.Persist
import de.jhoffmann.api.FTScraper.{Article, Attachment, Tag}

object NoteCreator {
  private val fmt = DateTimeFormatter.RFC_1123_DATE_TIME

  def props(makeNotePersistor: ActorContext => ActorRef): Props = Props(new NoteCreator(makeNotePersistor))

  case class CreateNote(article: Article)
}

class NoteCreator(makeNotePersistor: ActorContext => ActorRef) extends Actor with ActorLogging {

  private val notePersistor = makeNotePersistor(context)

  override def receive: Receive = {
    case CreateNote(article) =>
      val attachments = article.attachments.map(makeFragment)
      val tags        = article.tags.filter(_.url.nonEmpty).map(makeFragment).mkString("/ ")
      val content     = makeContent(article, attachments, tags)
      val note        = new Note
      note.setTitle(article.title)
      note.setContent(content)
      note.setCreated(article.timestamp.toInstant.getEpochSecond * 1000L)
      val attr = new NoteAttributes
      attr.setSourceURL(article.url)
      note.setAttributes(attr)
      article.tags.map(t => s"ft_${t.name.trim.replaceAll("[ _,]+", "_").toLowerCase}").foreach(note.addToTagNames)
      log.debug(s"Created note '${note.getTitle}'")
      notePersistor ! Persist(note)
  }

  private def makeFragment(att: Attachment) =
    s"""
       |<hr/>
       |<div>Attachment: <a href="${att.escapedUrl}">${att.escapedName}</a></div>
     """.stripMargin.replaceAll("\n", "")

  private def makeFragment(tag: Tag) =
    s"""
       |<a href="${tag.url}">${tag.escapedName}</a>
     """.stripMargin.replaceAll("\n", "")

  private def makeContent(article: Article, attachments: List[String], tags: String) =
    s"""
       |<?xml version="1.0" encoding="UTF-8"?>
       |<!DOCTYPE en-note SYSTEM "http://xml.evernote.com/pub/enml2.dtd">
       |<en-note>
       |<div><b>${article.escapedTitle}</b><em> - <a href="${article.url}">Source</a></em></div>
       |<div><em>$tags</em></div>
       |<div><em>
       |<a href="${article.author.url}">${article.author.escapedName}</a> @ ${NoteCreator.fmt.format(article.timestamp)}
       |</em></div>
       |<div><em>${attachments.size} attachment(s)</em></div>
       |<hr/>
       |${article.escapedContent}
       |${attachments.mkString}
       |</en-note>
    """.stripMargin.replaceAll("\n", "")

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    message match {
      case Some(CreateNote(article)) => log.warning(s"Could not create not for ${article.url}")

      case _ =>
    }
    super.preRestart(reason, message)
  }

}
