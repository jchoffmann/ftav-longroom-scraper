import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import FTScraper.Links
import org.jsoup.{Connection, Jsoup}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.StdIn

object FTScraper {

  private val landingPages =
    Set("https://ftalphaville.ft.com/longroom/home", "https://ftalphaville.ft.com/longroom/home/?page=1")

  private val fmt = DateTimeFormatter.ofPattern("E MMM dd uuuu HH:mm:ss zZ (z)")

  private val titleKeywordsThatIndicateRequests   = List("?", "request", "anyone")
  private val contentKeywordsThatIndicateRequests = List("request", "anyone")

  private val mimeDB = mutable.Map.empty[String, String]
  mimeDB.put("pdf", "application/pdf")
  mimeDB.put("jpg", "image/jpeg")
  mimeDB.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

  private def queryMimeDB(ext: String) =
    mimeDB.getOrElseUpdate(ext.trim.toLowerCase, StdIn.readLine(s"New MIME type for extension $ext: ").trim.toLowerCase)

  case class Links(nextPages: Set[String] = Set.empty, articleLinks: List[(String, String)] = Nil) {
    def ++(other: Links) = Links(nextPages ++ other.nextPages, articleLinks ++ other.articleLinks)
  }

}

class FTScraper(sessionId: String) {

  private def connection(url: String): Connection =
    Jsoup
      .connect(url)
      .cookie("FTSession", sessionId)
      .header("Accept-Encoding", "gzip, deflate")
      .maxBodySize(0)
      .timeout(120 * 1000)

  def scrapeLinks(maxDepth: Int = Int.MaxValue): Links = {
    def parseLinks(url: String): Links = {
      val doc       = connection(url).get
      val pageLinks = doc.select("a[href*=longroom][data-trackable=link]").eachAttr("abs:href").asScala.toSet
      val articleLinks =
        doc.select("a[href*=longroom][data-trackable=heading]").asScala.map(e => (e.text, e.attr("abs:href"))).toList
      Links(pageLinks, articleLinks)
    }
    @tailrec
    def scrapeLinksR(urls: Set[String], seen: Links, depth: Int): Links = {
      println(s"Level $depth: Parsing $urls")
      if (urls.isEmpty || depth >= maxDepth)
        seen
      else {
        val current = urls.map(parseLinks).reduceLeft(_ ++ _)
        val newSeen = Links(seen.nextPages ++ urls, seen.articleLinks ++ current.articleLinks)
        scrapeLinksR(current.nextPages -- newSeen.nextPages, newSeen, depth + 1)
      }
    }
    scrapeLinksR(FTScraper.landingPages, Links(), 0)
  }

  def scrapeArticle(url: String, downloadAttachments: Boolean = false): Article = {
    def scrapeAttachment(url: String, fileIcon: String) = {
      val fileName = url.toString.split("/").last
      val (name, ext) =
        if (fileName.contains("."))
          (fileName, fileName.split("\\.").last)
        else {
          val ext = fileIcon.split("[/\\.]").dropRight(1).last
          (s"$fileName.$ext", ext)
        }
      val mime    = FTScraper.queryMimeDB(ext)
      val content = if (downloadAttachments) connection(url).execute.bodyAsBytes else Array.emptyByteArray
      Attachment(name, mime, content, url)
    }
    def classifyRequest(article: Article) = {
      val hasAttachments  = article.attachments.nonEmpty
      val hasTitleKeyword = FTScraper.titleKeywordsThatIndicateRequests.exists(article.title.toLowerCase.contains)
      val hasContentKeyword =
        FTScraper.contentKeywordsThatIndicateRequests.exists(article.content.toLowerCase.contains)
      if (!hasAttachments && (hasTitleKeyword || hasContentKeyword))
        article.copy(tags = Tag("request", "") +: article.tags)
      else
        article
    }
    val doc   = connection(url).get
    val title = doc.select(".alphaville-card__heading").text
    val author =
      doc.select(".alphaville-card__author a").asScala.map(e => Author(e.text, e.attr("abs:href"))).head
    val timestamp = doc.select("time").attr("datetime")
    val tags =
      doc.select(".alphaville-card__tag").asScala.map(e => Tag(e.text.toLowerCase, e.attr("abs:href"))).toList
    val content = doc.select(".lr-post-body").text
    val attachments = doc
      .select("a[href*=files]")
      .asScala
      .map(a => (a.attr("abs:href"), a.select(".lr-card__file-icon").attr("src")))
      .filter(_._2.nonEmpty)
      .map((scrapeAttachment _).tupled)
      .toList
    val article =
      Article(title, author, ZonedDateTime.from(FTScraper.fmt.parse(timestamp)), tags, content, attachments, url)
    classifyRequest(article)
  }

}