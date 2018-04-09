import com.evernote.edam.error.EDAMSystemException
import com.google.common.util.concurrent.RateLimiter
import pb.ProgressBar

import scala.util.{Failure, Success, Try}

object Main extends App {
  val banner =
    s"""
       |=======================================
       |=== FT Alphaville Long Room Scraper ===
       |=======================================
     """.stripMargin

  // TODO Add these as command line properties
  val sessionId      = "..."
  val developerToken = "..."
  val notebook       = "FTAlphaVille Long Room"

  // TODO Change to use Akka actors
  val sc = new FTScraper(sessionId)
  val ev = new EvernoteApi(developerToken)

  println(banner)
  println

  // Check what we have not yet downloaded
  val notebookId = ev.findNoteBook(notebook).get.getGuid
  println("Collecting notes from Evernote...")
  val notes = ev.findNoteTitles(notebookId)
  println("Scraping article links from FT...")
  println
  val links = sc.scrapeLinks(maxDepth = Int.MaxValue) // TODO CHANGE
  val linksToScrape = for {
    (article, link) <- links.articleLinks if !notes.contains(article)
  } yield link

  println
  println(s"Found ${notes.diff(links.articleLinks.map(_._1)).size} notes not seen on FT")
  println(s"Found ${linksToScrape.size} articles out of ${links.articleLinks.size} that are new and will be scraped")
  println

  // Scrape
  val throttle    = RateLimiter.create(2.0)
  val pb          = new ProgressBar(linksToScrape.size)
  var attachments = 0
  var bytesTotal  = 0
  linksToScrape
    .foreach(link => {
      pb += 1
      val article = sc.scrapeArticle(link, downloadAttachments = false)
      attachments += article.attachments.size
      bytesTotal += article.attachments.map(_.content.length).sum
      val maybeNote = Try {
        ev.creatNote(article)(
//          EvernoteApi.makeFileAttachment(_)(
//            Paths.get(s"${System.getProperty("user.home")}/Documents/FTAVLongRoom"),
//            shouldSave = false)
          EvernoteApi.makeInlineAttachment
        )
      }
      throttle.acquire
      maybeNote match {
        case Success(note)                   => ev.persistNote(note, notebookId)
        case Failure(t: EDAMSystemException) => throw t
        case Failure(t)                      => println(s"Failed to create note '${article.title}'. Reason: $t")
      }
    })

  // Report
  def humanReadableByteSize(fileSize: Long): String =
    if (fileSize <= 0) "0 B"
    else {
      val units: Array[String] = Array("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
      val digitGroup: Int      = (Math.log10(fileSize) / Math.log10(1024)).toInt
      f"${fileSize / Math.pow(1024, digitGroup)}%3.3f ${units(digitGroup)}"
    }

  println
  println
  println(s"Scraped ${pb.current} articles")
  println(s"Scraped $attachments attachments totalling ${humanReadableByteSize(bytesTotal)}")
  println

}
