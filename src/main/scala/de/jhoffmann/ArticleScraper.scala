package de.jhoffmann

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import com.google.common.util.concurrent.RateLimiter
import de.jhoffmann.ArticleScraper.ScrapeArticle
import de.jhoffmann.NoteCreator.CreateNote

object ArticleScraper {
  def props(rate: Double, ft: api.FTScraper, makeNoteCreator: ActorContext => ActorRef) =
    Props(new ArticleScraper(rate, ft, makeNoteCreator))

  case class ScrapeArticle(url: String)
}

class ArticleScraper(rate: Double, ft: api.FTScraper, makeNoteCreator: ActorContext => ActorRef)
    extends Actor
    with ActorLogging {

  private val throttle    = RateLimiter.create(rate)
  private val noteCreator = makeNoteCreator(context)

  override def receive: Receive = {
    case ScrapeArticle(url) =>
      throttle.acquire
      val article = ft.scrapeArticle(url)
      log.debug(s"Scraped article '${article.title}'")
      noteCreator ! CreateNote(article)
  }
}
