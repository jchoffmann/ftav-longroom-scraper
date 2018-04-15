package de.jhoffmann

import akka.actor.{ActorContext, ActorSystem}
import de.jhoffmann.ArticleScraper.ScrapeArticle
import de.jhoffmann.ProgressReporter.Show
import de.jhoffmann.api.{Evernote, FTScraper}

object Main extends App {
  val opts = CommandLine
    .parse(args, CommandLineConfig())
    .getOrElse(throw new RuntimeException("Invalid command line parameters"))

  // Set up APIs
  val ft         = new FTScraper(opts.sessionId)
  val ev         = new Evernote(opts.devToken)
  val notebookId = ev.findNoteBook(opts.noteBook).get.getGuid

  // Set up Actors
  val actors           = ActorSystem.create
  val progressReporter = actors.actorOf(ProgressReporter.props, "progressReporter")
  val makeNotePersistor = (ctx: ActorContext) =>
    ctx.actorOf(NotePersistor.props(opts.persistRate, ev, notebookId), "notePersistor")
  val makeNoteCreator = (ctx: ActorContext) => ctx.actorOf(NoteCreator.props(makeNotePersistor), "noteCreator")
  val articleScraper  = actors.actorOf(ArticleScraper.props(opts.scrapeRate, ft, makeNoteCreator), "articleScraper")

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
