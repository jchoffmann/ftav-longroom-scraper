package de.jhoffmann

case class CommandLineConfig(sessionId: String = "",
                             devToken: String = "",
                             noteBook: String = "",
                             scrapeRate: Double = 1.0,
                             persistRate: Double = 500D / (60D * 60D)) // EM seems to accept 500 created notes per hour

object CommandLine extends scopt.OptionParser[CommandLineConfig]("sbt run") {
  head("FT Alphaville Long Room Scraper", s"v${de.jhoffmann.BuildInfo.version}")

  opt[String]("sessionId")
    .required()
    .action((x, c) => c.copy(sessionId = x))
    .text("session ID for a logged in session on ftalphaville.ft.com")

  opt[String]("devToken")
    .required()
    .action((x, c) => c.copy(devToken = x))
    .text("Evernote developer token")

  opt[String]("noteBook")
    .required()
    .action((x, c) => c.copy(noteBook = x))
    .text("target notebook in Evernote")

  opt[Double]("scrapeRate")
    .action((x, c) => c.copy(scrapeRate = x))
    .text("rate at which FT articles are scraped (articles per second)")

  opt[Double]("persistRate")
    .action((x, c) => c.copy(persistRate = x))
    .text("rate at which notes are created in Evernote (notes per second)")

  help("help").text("prints this usage text")
}
