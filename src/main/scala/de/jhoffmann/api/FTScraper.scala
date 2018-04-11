package de.jhoffmann.api

import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.jsoup.{Connection, Jsoup}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.StdIn
import scala.xml.Utility

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

  // Domain

  case class Article(title: String,
                     author: Author,
                     timestamp: ZonedDateTime,
                     tags: List[Tag],
                     content: String,
                     attachments: List[Attachment],
                     url: String) {

    def escapedTitle: String = Utility.escape(title)

    def escapedContent: String = Utility.escape(content)
  }

  case class Author(name: String, url: String) {
    def escapedName: String = Utility.escape(name)
  }

  case class Tag(name: String, url: String) {
    def escapedName: String = Utility.escape(name)
  }

  case class Attachment(name: String, mime: String, content: Array[Byte], url: String) {
    def hasContent: Boolean = content.nonEmpty

    def hash: Array[Byte] = MessageDigest.getInstance("MD5").digest(content)

    def hashHex: String = hash.map("%02X" format _).mkString

    def escapedName: String = Utility.escape(name)

    def escapedUrl: String = Utility.escape(url)
  }

  type ArticleLinks = Map[String, String]

  case class Links(urls: Set[String] = Set.empty, articles: ArticleLinks = Map.empty) {
    def ++(other: Links) = Links(urls ++ other.urls, articles ++ other.articles)
  }

}

class FTScraper(sessionId: String) {

  import FTScraper._

  private def connection(url: String): Connection =
    Jsoup
      .connect(url)
      .cookie("FTSession", sessionId)
      .header("Accept-Encoding", "gzip, deflate")
      .maxBodySize(0)
      .timeout(120 * 1000)

  def scrapeLinks(callback: (Int, ArticleLinks) => Unit, maxDepth: Int = Int.MaxValue): ArticleLinks = {
    def parseLinks(url: String): Links = {
      val doc       = connection(url).get
      val pageLinks = doc.select("a[href*=longroom][data-trackable=link]").eachAttr("abs:href").asScala.toSet
      val articleLinks =
        doc.select("a[href*=longroom][data-trackable=heading]").asScala.map(e => (e.text, e.attr("abs:href"))).toMap
      Links(pageLinks, articleLinks)
    }
    @tailrec
    def parseLinksR(urls: Set[String], seen: Set[String], depth: Int, acc: Links): ArticleLinks = {
      if (urls.isEmpty || depth >= maxDepth)
        acc.articles
      else {
        val current = (urls -- seen).map(parseLinks).foldLeft(Links())(_ ++ _)
        callback(depth, current.articles)
        parseLinksR(current.urls, seen ++ urls, depth + 1, acc ++ current)
      }
    }
    parseLinksR(landingPages, Set.empty, 0, Links())
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
      val mime    = queryMimeDB(ext)
      val content = if (downloadAttachments) connection(url).execute.bodyAsBytes else Array.emptyByteArray
      Attachment(name, mime, content, url)
    }
    def classifyRequest(article: Article) = {
      val hasAttachments  = article.attachments.nonEmpty
      val hasTitleKeyword = titleKeywordsThatIndicateRequests.exists(article.title.toLowerCase.contains)
      val hasContentKeyword =
        contentKeywordsThatIndicateRequests.exists(article.content.toLowerCase.contains)
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
      Article(title, author, ZonedDateTime.from(fmt.parse(timestamp)), tags, content, attachments, url)
    classifyRequest(article)
  }

}
