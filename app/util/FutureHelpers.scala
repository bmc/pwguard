package util

import scala.concurrent.Future

object FutureHelpers {

  /** Construct a failed future from a message.
    *
    * @param msg  the message
    */
  def failedFuture[T](msg: String): Future[T] = {
    Future.failed[T](new Exception(msg))
  }
}
