package util

/** Some helpful Option and Either conversions
  */
object EitherOptionHelpers {

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
