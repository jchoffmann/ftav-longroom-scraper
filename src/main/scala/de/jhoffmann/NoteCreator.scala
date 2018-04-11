package de.jhoffmann

import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import com.evernote.edam.`type`.Note
import de.jhoffmann.NoteCreator.CreateNote
import de.jhoffmann.NotePersistor.Persist
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
      val attachments = article.attachments.map(makeAttachment)
      val tags        = article.tags.filter(_.url.nonEmpty).map(makeTag).mkString("/ ")
      val content     = makeContent(article, attachments, tags)
      val note        = new Note
      note.setTitle(article.title)
      note.setContent(content)
      note.setCreated(article.timestamp.toInstant.getEpochSecond * 1000L)
      article.tags.map(t => s"ft_${t.name.trim.replaceAll(" +", "_").toLowerCase}").foreach(note.addToTagNames)
      log.debug(s"Created note '${note.getTitle}'")
      notePersistor ! Persist(note)
  }

  private def makeAttachment(att: Attachment) =
    s"""
       |<hr/>
       |<div>Attachment: <a href="${att.escapedUrl}">${att.escapedName}</a></div>
     """.stripMargin.replaceAll("\n", "")

  private def makeTag(tag: Tag) =
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

}
