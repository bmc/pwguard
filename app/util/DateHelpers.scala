package util

import java.sql.Timestamp
import java.util.{ Calendar, Date }
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime => JodaDateTime, Duration => JodaDuration}
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.util.{ Try, Success, Failure }

/** Helpers for Date and Timestamp classes.
  */
object DateHelpers {

  lazy val isoDateParser = ISODateTimeFormat.dateTimeParser()


  /** Parse an ISO time.
    *
    * @param date  the date/time string to parse
    *
    * @return `Right(dt)` with a Joda `DateTime`, on success.
    *         `Left(error)` on failure.
    */
  def parseISODateTime(date: String): Either[String, JodaDateTime] = {
    Try(isoDateParser.parseDateTime(date)) match {
      case Success(dt) => Right(dt)
      case Failure(e)  => Left(e.getMessage)
    }
  }

  /** Format a date-time as an ISO date-time.
    *
    * @param d  The Joda `DateTime`
    *
    * @return The ISO time string
    */
  def formatISODate(d: JodaDateTime): String = d.toDateTimeISO.toString

  /** Format a Java date as an ISO date-time.
    *
    * @param d  the Java `Date`
    *
    * @return The ISO time string
    */
  def formatISODate(d: Date): String = formatISODate(new JodaDateTime(d))

  /** Implicits. Import to get them.
    */
  object Implicits {

    /** Enriched Timestamp
      */
    implicit class RichTimestamp(timestamp: Timestamp) {

      /** Convert this SQL timestamp to a Date object.
        *
        * @return the date
        */
      def toDate: Date = new Date(timestamp.getTime)

      /** Convert this SQL timestamp to a Joda DateTime object.
        *
        * @return the datetime
        */
      def toDateTime: JodaDateTime = new JodaDateTime(timestamp.getTime)
    }

    /** Enriched Date
      */
    implicit class RichDate(date: Date) {

      /** Convert this date to a SQL timestamp object.
        *
        * @return the timestamp
        */
      def toTimestamp: Timestamp = new Timestamp(date.getTime)

      /** Convert the date/time to the same date, but at midnight.
        *
        * @return the beginning of the day (midnight)
        */
      def dayStart: Date = {
        val cal = Calendar.getInstance()
        cal.setTime(date)
        cal.set(Calendar.HOUR, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.getTime
      }

      /** Convert the date/time to the same date, but the end of the day
        * (i.e., 11:59:59 PM).
        *
        * @return the end of the day.
        */
      def dayEnd: Date = {
        val cal = Calendar.getInstance()
        cal.setTime(date)
        cal.set(Calendar.HOUR, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        cal.getTime
      }
    }

    /** Enriched Joda DateTime.
      */
    implicit class RichJodaDateTime(datetime: JodaDateTime) {

      /** Convert this datetime to a SQL timestamp object.
        *
        * @return the timestamp
        */
      def toTimestamp: Timestamp = new Timestamp(datetime.getMillis)
    }

    /** Enriched Joda Duration
      */
    implicit class RichJodaDuration(duration: JodaDuration) {

      /** Convert this object to a Scala concurrent duration.
        *
        * @return the Scala duration
        */
      def toScalaDuration: Duration = Duration(duration.getMillis, MILLISECONDS)
    }

    /** Enriched Scala duration.
      */
    implicit class RichScalaDuration(duration: Duration) {

      /** Convert this object to a Joda duration.
        *
        * @return the Joda duration
        */
      def toJodaDuration: JodaDuration = JodaDuration.millis(duration.toMillis)
    }
  }
}
