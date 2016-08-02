//
// Copyright 2016 Commonwealth Bank of Australia
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//        http://www.apache.org/licenses/LICENSE-2.0
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//

package commbank.coppersmith.util

import java.util.concurrent.TimeUnit.MILLISECONDS

import scala.util.{Failure, Success, Try}
import scalaz.Order

import org.joda.time.{DateTime, Period, LocalDate, DateTimeZone}
import org.joda.time.format.DateTimeFormat

import commbank.coppersmith.util.Timestamp.Offset

case class DatePeriod(years: Int, months: Int, days: Int)

object Datestamp {
  val parseDefault       = parseFormat("yyyy-MM-dd")
  val unsafeParseDefault = unsafeParseFormat("yyyy-MM-dd")

  def parse(date: String): Either[(String, String), Datestamp] = parseDefault(date)

  def unsafeParse(date: String): Datestamp = unsafeParseDefault(date)

  def parseFormat(pattern: String): (String => Either[(String, String), Datestamp]) = {
    val fmt = DateTimeFormat.forPattern(pattern)
    time => {
      Try(fmt.parseLocalDate(time)) match {
        case Success(d) => Right(Datestamp(d.getYear, d.getMonthOfYear, d.getDayOfMonth))
        case Failure(_) => Left((time, pattern))
      }
    }
  }

  def unsafeParseFormat(pattern: String): (String => Datestamp) = {
    val f = parseFormat(pattern)
    time => f(time).right.getOrElse(sys.error(s"Unable to parse date: ${f(time).left.get}"))
  }

  implicit def ordering[A <: Datestamp]: Ordering[A] = Ordering.by(_.toIso8601ExtendedFormatString)
  implicit def scalazOrder[A <: Datestamp]: Order[A] = Order.fromScalaOrdering(ordering)
}

object Timestamp {
  type Offset = Option[(Int, Int)]

  val parseWithMillisDefault          = parseFormatWithOffset("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")
  val parseWithoutMillisDefault       = parseFormatWithOffset("yyyy-MM-dd'T'HH:mm:ssZZ")
  val unsafeParseWithMillisDefault    = unsafeParseFormatWithOffset("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")
  val unsafeParseWithoutMillisDefault = unsafeParseFormatWithOffset("yyyy-MM-dd'T'HH:mm:ssZZ")

  /**
    * Parses a timestamp in RFC3339 format with millisecond precision.
    *
    * @param time The timestamp to parse
    * @return Either the parsed timestamp, or the time arg and pattern used if parsing fails
    */
  def parseWithMillis(time: String): Either[(String, String), Timestamp] =
    parseWithMillisDefault(time, parseOffset(time))

  /**
    * Parses a timestamp in RFC3339 format with millisecond precision. An exception is thrown if
    * parsing fails.
    *
    * @param time The timestamp to parse
    * @return The parsed timestamp
    */
  def unsafeParseWithMillis(time: String): Timestamp =
    unsafeParseWithMillisDefault(time, parseOffset(time))

  /**
    * Parses a timestamp in RFC3339 format without millisecond precision.
    *
    * @param time The time string to parse
    * @return Either the parsed Timestamp, or the time arg and pattern used if parsing fails
    */
  def parseWithoutMillis(time: String): Either[(String, String), Timestamp] =
    parseWithoutMillisDefault(time, parseOffset(time))

  /**
    * Parses a timestamp in RFC3339 format without millisecond precision. An exception is thrown if
    * parsing fails.
    *
    * @param time The time string to parse
    * @return The parsed Timestamp
    */
  def unsafeParseWithoutMillis(time: String): Timestamp =
    unsafeParseWithoutMillisDefault(time, parseOffset(time))

  /**
    * Creates a parse function for a pattern. Note: The pattern must parse timezone information.
    *
    * @param pattern The pattern to use (Must parse timezone)
    * @return A function from a time string to either the parsed Timestamp,
    *         or the time arg and pattern used if parsing fails
    */
  def parseFormat(pattern: String): String => Either[(String, String), Timestamp] = {
    // Remove literals
    val p = pattern.replaceAll("'[^']*'", "")
    if (!p.contains("Z")) throw new IllegalArgumentException(s"$pattern doesn't parse timezones.")

    val fmt = DateTimeFormat.forPattern(pattern)
    time => {
      val triedTime = Try {
        val dt     = fmt.withOffsetParsed.parseDateTime(time)
        val tz     = dt.getZone.getOffset(dt)
        val offset = Some((MILLISECONDS.toHours(tz).toInt,
          Math.abs(MILLISECONDS.toMinutes(tz).toInt % 60)))

        Timestamp(dt.getMillis, offset)
      }
      Either.cond(triedTime.isSuccess, triedTime.get, (time, pattern))
    }
  }

  /**
    * Creates an unsafe parse function for a pattern. Note: The pattern must parse timezone
    * information. An exception is thrown if parsing fails.
    *
    * @param pattern The pattern to use (Must parse timezone)
    * @return An unsafe parse function from time string to Timestamp
    */
  def unsafeParseFormat(pattern: String): String => Timestamp = {
    val f = parseFormat(pattern)
    s => f(s).right.getOrElse(sys.error(s"Unable to parse time: ${f(s).left.get}"))
  }

  /**
    * Creates a parse function for a pattern. The function should be used to provide offset
    * information missing from the timestamp, or to overwrite offset information.
    * Note: The time will not be adjusted to the new offset, the existing offset will be replaced.
    *
    * @param pattern The pattern to use to parse
    * @return A function from a time string and offset to either the parsed Timestamp,
    *         or the time arg and pattern used if parsing fails
    */
  def parseFormatWithOffset(pattern: String): (String, Offset) => Either[(String, String), Timestamp] = {
    val fmt = DateTimeFormat.forPattern(pattern)
    (time, offset) => {
      val (h, m)  = offset.getOrElse((0, 0))
      val tz      = DateTimeZone.forOffsetHoursMinutes(h, m)
      // Without withOffsetParsed the timezone fields are moved to system timezone
      val triedDT = Try(fmt.withOffsetParsed().parseDateTime(time).withZoneRetainFields(tz))

      Either.cond(triedDT.isSuccess, Timestamp(triedDT.get.getMillis, offset), (time, pattern))
    }
  }

  /**
    * Creates an unsafe parse function for a pattern. The function should be used to provide offset
    * information missing from the timestamp, or to overwrite offset information.
    * Note: The time will not be adjusted to the new offset, the existing offset will be replaced.
    *
    * @param pattern The pattern to use to parse
    * @return An unsafe function from a time string to a parsed Timestamp
    */
  def unsafeParseFormatWithOffset(pattern: String): (String, Offset) => Timestamp = {
    val f = parseFormatWithOffset(pattern)
    (s, o) => f(s, o).right.getOrElse(sys.error(s"Unable to parse time: ${f(s, o).left.get}"))
  }

  private def parseOffset(time: String): Option[(Int, Int)] = {
    // Parse timezone hour and minute
    val tzParser =
      """.*([\-+]\d{2}):(\d{2}).*""".r

    // -00:00 represents unknown timezone
    val offset = time match {
      case tzParser("-00", "00") => None
      case tzParser(h, m) => Some((h.toInt, m.toInt))
      case _ => None
    }
    offset
  }

  implicit def ordering[A <: Timestamp]: Ordering[A] = Ordering.by(t => (t.millis, t.offset))
  implicit def scalazOrder[A <: Timestamp]: Order[A] = Order.fromScalaOrdering(ordering)
}

case class Datestamp(year: Int, month: Int, day: Int) {
  protected def toLocalDate: org.joda.time.LocalDate ={
    new LocalDate(year, month, day)
  }

  def difference(that: Datestamp): DatePeriod = that match {
    case Datestamp(y, m, d) =>
      val p = new Period(this.toLocalDate, that.toLocalDate)
      DatePeriod(p.getYears, p.getMonths, p.getDays)
  }

  def toIso8601ExtendedFormatString: String = {
    f"$year%04d-$month%02d-$day%02d"
  }
}

case class Timestamp(millis: Long, offset: Offset) {

  def toUTC: Timestamp = {
    val dt = toDateTime.toDateTime(DateTimeZone.UTC)
    Timestamp.unsafeParseWithMillis(dt.toString("yyyy-MM-dd'T'HH:mm:ss.SSSZZ"))
  }

  protected def toDateTime: org.joda.time.DateTime ={
    val (h, m) = offset.getOrElse((0,0))
    val tz = DateTimeZone.forOffsetHoursMinutes(h, m)
    new DateTime(millis, tz)
  }

  def toRfc3339String: String = {
    val offsetStr = offset.map { case ((h, m)) => f"$h%+03d:$m%02d" }.getOrElse("-00:00")
    f"${toDateTime.toString("yyyy-MM-dd'T'HH:mm:ss.SSS")}$offsetStr"
  }
}
