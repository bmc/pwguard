package util

import scala.concurrent.Future

/** Some helpful Option and Either conversions
  */
object EitherOptionHelpers {

  /** Create a `None` of a specific type.
    *
    * @tparam T  the type
    *
    * @return an appropriate `None`
    */
  def noneT[T] = {
    val n: Option[T] = None
    n
  }

  /** Take an `Option[String]` and, if it contains a string, strip the white
    * space, mapping an empty string to a `None`.
    *
    * @param opt  The option
    *
    * @return the mapped option
    */
  def blankToNone(opt: Option[String]): Option[String] = {
    opt.flatMap { s => if (s.trim().length == 0 ) None else Some(s) }
  }

  object Implicits {

    /** Enriched `Option` class.
      *
      * @param opt the option
      * @tparam T  the option's type
      */
    implicit class RichOption[T](opt: Option[T]) {

      /** Convert an option to a `Right`, if it contains a value. If there
        * is no value, it returns a `Left` with the specified value.
        *
        * @param leftValue  the value to use if the option is `None`
        *
        * @tparam L  The type of the `Left` value
        *
        * @return `Right(value)` if the option is a `Some`, or `Left`
        *         if the option is a `None`.
        */
      def toEither[L](leftValue: L): Either[L, T] = {
        opt map { value => Right(value) } getOrElse Left(leftValue)
      }

      /** Convert an option to a Future. If the option has a value,
        * it's converted to a successful future. Otherwise, it's converted
        * to a failed future.
        *
        * @param error message to use for an empty option
        */
      def toFuture(error: String): Future[T] = {
        opt.map { Future.successful(_) }
           .getOrElse { Future.failed(new Exception(error)) }
      }
    }

    /** Enriched `Either` class.
      *
      * @param either  the `Either`
      * @tparam L      the type of the `Left` projection
      * @tparam R      the type of the `Right` projection
      */
    implicit class RichEither[L, R](either: Either[L, R]) {

      /** Convert an `Either` to an `Option`. If the either is a `Right`,
        * it's converted to a `Some` containing the value. If the either
        * is a `Left`, it's converted to a `None` (and the `Left` value is
        * lost).
        *
        * @return the converted option
        */
      def toOption: Option[R] = either.fold(_ => None, r => Some(r))
    }
  }

}
