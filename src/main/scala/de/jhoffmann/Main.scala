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

  // APIs
  val ft = new FTScraper(sessionId)
  val ev = new Evernote(developerToken)

  // Check what we have not yet downloaded
  val notebookId = ev.findNoteBook(notebook).get.getGuid
  println("Collecting notes from Evernote ...")
  val notes = ev.findNoteTitles(notebookId).toSet
  println(s"Found ${notes.size} notes")

  // Set up actors
  val persistRate      = 500D / (60D * 60D)
  val scrapeRate       = 1D
  val actors           = ActorSystem.create
  val progressReporter = actors.actorOf(ProgressReporter.props, "progressReporter")
  val makeNotePersistor = (ctx: ActorContext) =>
    ctx.actorOf(NotePersistor.props(persistRate, ev, notebookId), "notePersistor")
  val makeNoteCreator = (ctx: ActorContext) => ctx.actorOf(NoteCreator.props(makeNotePersistor), "noteCreator")
  val articleScraper  = actors.actorOf(ArticleScraper.props(scrapeRate, ft, makeNoteCreator), "articleScraper")

  // Scrape links
  var total = 0
  ft.scrapeLinks(
    (level, links) => {
      val filtered = links.filterKeys(!notes.contains(_))
      println(s"Level $level: Ignored ${links.size - filtered.size}, found ${filtered.size}: $filtered")
      filtered.values.foreach(articleScraper ! ScrapeArticle(_))
      total += filtered.size
    },
    maxDepth = Int.MaxValue
  )
  println
  progressReporter ! Show(total)
}
