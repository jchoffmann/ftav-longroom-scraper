import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.Path
import java.time.format.DateTimeFormatter

import EvernoteApi.AttachmentFactory
import com.evernote.auth.{EvernoteAuth, EvernoteService}
import com.evernote.clients.ClientFactory
import com.evernote.edam.`type`._
import com.evernote.edam.notestore.{NoteFilter, NotesMetadataResultSpec}

import scala.collection.JavaConverters._
import scala.xml.Utility

object EvernoteApi {

  private val fmt = DateTimeFormatter.RFC_1123_DATE_TIME

  type AttachmentFragment = String
  type AttachmentContent  = (AttachmentFragment, Option[Resource])
  type AttachmentFactory  = (Attachment => AttachmentContent)

  def makeInlineAttachment(att: Attachment): AttachmentContent = {
    val fragment = {
      if (att.hasContent)
        s"""
           |<hr/>
           |<div>Attachment: <a href="${att.escapedUrl}">${att.escapedName}</a></div>
           |<div><en-media type="${att.mime}" hash="${att.hashHex}"/></div>
         """
      else
        s"""
           |<hr/>
           |<div>Attachment: <a href="${att.escapedUrl}">${att.escapedName}</a></div>
         """
    }.stripMargin.replaceAll("\n", "")
    val maybeResource =
      if (att.hasContent) {
        val data = new Data
        data.setSize(att.content.length)
        data.setBodyHash(att.hash)
        data.setBody(att.content)
        val res = new Resource
        res.setData(data)
        res.setMime(att.mime)
        val attr = new ResourceAttributes
        attr.setFileName(att.name)
        res.setAttributes(attr)
        Some(res)
      } else None
    (fragment, maybeResource)
  }

  def makeFileAttachment(att: Attachment)(parentDir: Path, shouldSave: Boolean = false): AttachmentContent = {
    val path = parentDir.resolve(att.name)
    val fragment = {
      if (att.hasContent)
        s"""
           |<hr/>
           |<div>Attachment: <a href="${att.escapedUrl}">${att.escapedName}</a></div>
           |<div>File copy:&nbsp;
           |<a href="${Utility.escape(path.toUri.toASCIIString)}">${Utility.escape(path.getFileName.toString)}</a>
           |</div>
         """
      else
        s"""
           |<hr/>
           |<div>Attachment: <a href="${att.escapedUrl}">${att.escapedName}</a></div>
         """
    }.stripMargin.replaceAll("\n", "")
    if (shouldSave && att.hasContent) {
      val os = new BufferedOutputStream(new FileOutputStream(path.toFile))
      os.write(att.content)
    }
    (fragment, None)
  }
}

class EvernoteApi(developerToken: String) {

  private lazy val noteStore = {
    val auth = new EvernoteAuth(EvernoteService.PRODUCTION, developerToken)
    new ClientFactory(auth).createNoteStoreClient
  }

  def findNoteTitles(notebookGuid: String): List[String] = {
    val filter = new NoteFilter
    filter.setNotebookGuid(notebookGuid)
    val spec = new NotesMetadataResultSpec
    spec.setIncludeTitle(true)
    noteStore.findNotesMetadata(filter, 0, Int.MaxValue, spec).getNotes.asScala.toList.map(_.getTitle)
  }

  def findNoteBook(name: String): Option[Notebook] = noteStore.listNotebooks.asScala.find(_.getName == name)

  def persistNote(note: Note, notebookGuid: String): Note = {
    note.setNotebookGuid(notebookGuid)
    noteStore.createNote(note)
  }

  def creatNote(article: Article)(makeAttachment: AttachmentFactory): Note = {
    val attachments  = article.attachments.map(makeAttachment)
    val tagsFragment = article.tags.filter(_.url.nonEmpty).map(tag => s"""
         |<a href="${tag.url}">${tag.escapedName}</a>
       """.stripMargin.replaceAll("\n", "")).mkString("/ ")
    val content =
      s"""
         |<?xml version="1.0" encoding="UTF-8"?>
         |<!DOCTYPE en-note SYSTEM "http://xml.evernote.com/pub/enml2.dtd">
         |<en-note>
         |<div><b>${article.escapedTitle}</b><em> - <a href="${article.url}">Source</a></em></div>
         |<div><em>$tagsFragment</em></div>
         |<div><em>
         |<a href="${article.author.url}">${article.author.escapedName}</a> @ ${EvernoteApi.fmt.format(
           article.timestamp)}
         |</em></div>
         |<div><em>${attachments.size} attachment(s)</em></div>
         |<hr/>
         |${article.escapedContent}
         |${attachments.map(_._1).mkString}
         |</en-note>
      """.stripMargin.replaceAll("\n", "")
    val note = new Note
    note.setTitle(article.title)
    note.setContent(content)
    note.setCreated(article.timestamp.toInstant.getEpochSecond * 1000L)
    article.tags.map(t => s"ft_${t.name.trim.replaceAll(" +", "_").toLowerCase}").foreach(note.addToTagNames)
    attachments.flatMap(_._2).foreach(note.addToResources)
    note
  }

}
