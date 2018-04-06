import java.security.MessageDigest
import java.time.ZonedDateTime

import scala.xml.Utility

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
