package io.kaizensolutions.event.logger

import io.kaizensolutions.event.logger.internal.{CanLog, EventValue}

final case class EventAttribute(key: String, value: EventValue)
object EventAttribute {
  def from[V](key: String, value: V)(implicit ev: CanLog[V]): EventAttribute =
    EventAttribute(key, EventValue.from(value))
}
