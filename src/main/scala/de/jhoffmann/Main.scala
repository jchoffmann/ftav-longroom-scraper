package de.jhoffmann

import akka.actor.{ActorContext, ActorSystem}
import de.jhoffmann.ArticleScraper.ScrapeArticle
import de.jhoffmann.ProgressReporter.Show
import de.jhoffmann.api.{Evernote, FTScraper}

object Main extends App {
  println(
    s"""
       |=======================================
       |=== FT Alphaville Long Room Scraper ===
       |=======================================
       |
     """.stripMargin
  )

  // TODO Add these as command line properties
  val sessionId      = "..."
  val developerToken = "..."
  val notebook       = "FTAlphaVille Long Room"

  // Set up APIs
  val ft         = new FTScraper(sessionId)
  val ev         = new Evernote(developerToken)
  val notebookId = ev.findNoteBook(notebook).get.getGuid

  // Set up actors
  val persistRate      = 500D / (60D * 60D)
  val scrapeRate       = 1D
  val actors           = ActorSystem.create
  val progressReporter = actors.actorOf(ProgressReporter.props, "progressReporter")
  val makeNotePersistor = (ctx: ActorContext) =>
    ctx.actorOf(NotePersistor.props(persistRate, ev, notebookId), "notePersistor")
  val makeNoteCreator = (ctx: ActorContext) => ctx.actorOf(NoteCreator.props(makeNotePersistor), "noteCreator")
  val articleScraper  = actors.actorOf(ArticleScraper.props(scrapeRate, ft, makeNoteCreator), "articleScraper")

  // Check what we have not yet downloaded
  println("Collecting notes from Evernote ...")
  val notes = ev.findNotes(notebookId).map(_.getAttributes.getSourceURL).filter(_ != null).toSet
  println(s"Found ${notes.size} notes")

  // Scrape links
  var total = 0
  ft.scrapeLinks(
    (level, links) => {
      val filtered = links -- notes
      println(s"Level $level: Ignored ${links.size - filtered.size}, found ${filtered.size}: $filtered")
      filtered.foreach(articleScraper ! ScrapeArticle(_))
      total += filtered.size
    },
    maxDepth = Int.MaxValue
  )
  println
  progressReporter ! Show(total)
}
