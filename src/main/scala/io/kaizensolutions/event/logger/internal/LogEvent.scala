package io.kaizensolutions.event.logger.internal

import io.kaizensolutions.event.logger.EventAttribute
import zio.Chunk

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

private[logger] final case class LogEvent(
  eventType: String,
  attributes: Set[EventAttribute],
  timestamp: Option[UnixTimestamp]
) {
  private def unsafeWrite(sb: StringBuilder): StringBuilder = {
    sb.append("{")
    sb.append(s"\"eventType\":\"$eventType\"")
    timestamp.fold(sb)(ts => sb.append(s",\"timestamp\":${ts.timestamp}"))
    attributes.foreach { case EventAttribute(k, v) =>
      sb.append(s",\"$k\":${v.render()}")
    }
    sb.append("}")
  }
}
private[logger] object LogEvent {
  def render(chk: Chunk[LogEvent]): String = {
    val sb = new StringBuilder()
    sb.append("[")
    // ---
    val lastElemIndex = chk.length - 1
    chk.indices.foreach { i =>
      chk(i).unsafeWrite(sb)
      if (i != lastElemIndex) sb.append(",")
    }
    // ---
    sb.append("]")
    sb.toString
  }

  def gzip(chk: Chunk[LogEvent]): Array[Byte] = {
    val input                 = render(chk)
    val byteArrayOutputStream = new ByteArrayOutputStream(input.length)
    val gzipOutputStream      = new GZIPOutputStream(byteArrayOutputStream)
    try gzipOutputStream.write(input.getBytes("UTF-8"))
    finally gzipOutputStream.close()
    byteArrayOutputStream.toByteArray
  }
}

private[logger] sealed trait EventValue {
  self =>
  import EventValue.*
  def render(): String = self match {
    case Str(value)   => s"\"$value\""
    case Intgr(value) => value.toString
    case Lng(value)   => value.toString
    case Dbl(value)   => value.toString
    case Flt(value)   => value.toString
    case Zdt(value)   => value.toInstant.toEpochMilli.toString
    case Odt(value)   => value.toInstant.toEpochMilli.toString
    case Ins(value)   => value.toEpochMilli.toString
  }
}
private[logger] object EventValue {
  final case class Str(value: String)                   extends EventValue
  final case class Intgr(value: Int)                    extends EventValue
  final case class Lng(value: Long)                     extends EventValue
  final case class Dbl(value: Double)                   extends EventValue
  final case class Flt(value: Float)                    extends EventValue
  final case class Zdt(value: java.time.ZonedDateTime)  extends EventValue
  final case class Odt(value: java.time.OffsetDateTime) extends EventValue
  final case class Ins(value: java.time.Instant)        extends EventValue

  def from[V](value: V)(implicit ev: CanLog[V]): EventValue =
    // https://contramap.dev/posts/2020-01-22-typesafe_reflection/
    ev match {
      case CanLog.CanLogString => EventValue.Str(value)
      case CanLog.CanLogInt    => EventValue.Intgr(value)
      case CanLog.CanLogLong   => EventValue.Lng(value)
      case CanLog.CanLogDouble => EventValue.Dbl(value)
      case CanLog.CanLogFloat  => EventValue.Flt(value)
      case CanLog.CanLogZdt    => EventValue.Zdt(value)
      case CanLog.CanLogOdt    => EventValue.Odt(value)
      case CanLog.CanLogIns    => EventValue.Ins(value)
    }
}

private[logger] sealed trait CanLog[A]
private[logger] object CanLog {
  implicit case object CanLogString extends CanLog[String]
  implicit case object CanLogInt    extends CanLog[Int]
  implicit case object CanLogLong   extends CanLog[Long]
  implicit case object CanLogDouble extends CanLog[Double]
  implicit case object CanLogFloat  extends CanLog[Float]
  implicit case object CanLogZdt    extends CanLog[java.time.ZonedDateTime]
  implicit case object CanLogOdt    extends CanLog[java.time.OffsetDateTime]
  implicit case object CanLogIns    extends CanLog[java.time.Instant]
}

final class UnixTimestamp(val timestamp: Long) extends AnyVal
