package de.jhoffmann.actors

import akka.actor.{Actor, Props}
import de.jhoffmann.actors.ProgressReporter.{Increment, Show}
import pb.ProgressBar

object ProgressReporter {

  def props: Props = Props[ProgressReporter]

  case object Increment

  case class Show(total: Int)

}

class ProgressReporter extends Actor {
  import context._

  override def receive: Receive = collecting(0)

  private def collecting(n: Int): Receive = {
    case Increment => become(collecting(n + 1))

    case Show(total) =>
      become(showing {
        val pb = new ProgressBar(total)
        pb.add(n)
        pb
      })
  }

  private def showing(pb: ProgressBar): Receive = {
    case Increment =>
      become(showing {
        pb.add(1)
        pb
      })
  }
}
