package util

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object FutureHelpers {

  /** Construct a failed future from a message.
    *
    * @param msg  the message
    */
  def failedFuture[T](msg: String): Future[T] = {
    Future.failed[T](new Exception(msg))
  }

  /** Map a `Try` to a `Future`, where the future's result is already
    * available.
    *
    * @param t  the `Try`
    *
    * @return the corresponding `Future`
    */
  def tryToFuture[T](t: Try[T]): Future[T] = {
    t match {
      case Success(v) => Future.successful(v)
      case Failure(e) => Future.failed(e)
    }
  }
}
