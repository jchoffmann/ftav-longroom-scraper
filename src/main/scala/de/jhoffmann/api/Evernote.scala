package de.jhoffmann.api

import java.time.ZonedDateTime

import com.evernote.auth.{EvernoteAuth, EvernoteService}
import com.evernote.clients.ClientFactory
import com.evernote.edam.`type`.{Note, Notebook, Resource}
import com.evernote.edam.notestore.{NoteFilter, NotesMetadataResultSpec}

import scala.collection.JavaConverters._

class Evernote(developerToken: String) {

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

  def createNote(title: String,
                 content: String,
                 created: ZonedDateTime,
                 tags: List[String],
                 resources: List[Resource]): Note = {
    val note = new Note
    note.setTitle(title)
    note.setContent(content)
    note.setCreated(created.toInstant.getEpochSecond * 1000L)
    tags.foreach(note.addToTagNames)
    resources.foreach(note.addToResources)
    note
  }

}
