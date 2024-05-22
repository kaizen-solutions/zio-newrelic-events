package io.kaizensolutions.event.logger.incidents

import io.kaizensolutions.event.logger.*
import io.kaizensolutions.event.logger.internal.{CanLog, EventValue}

sealed trait IncidentState { self =>
  private def sourceAttribute: Option[EventAttribute] = self match {
    case IncidentState.Trigger(source, _) => Option("source" := source)
    case IncidentState.Resolve            => None
  }

  private def titleAttribute: Option[EventAttribute] = self match {
    case IncidentState.Trigger(_, title) => Option("title" := title)
    case IncidentState.Resolve           => None
  }

  def toAttributes: Set[EventAttribute] = {
    val value = self match {
      case IncidentState.Trigger(_, _) => "trigger"
      case IncidentState.Resolve       => "resolve"
    }
    Set("state" := value) ++ titleAttribute ++ sourceAttribute
  }
}
object IncidentState {
  final case class Trigger(source: String, title: String) extends IncidentState
  case object Resolve                                     extends IncidentState
}

sealed trait IncidentPriority { self =>
  private def render(): String = self match {
    case IncidentPriority.Critical => "critical"
    case IncidentPriority.High     => "high"
    case IncidentPriority.Medium   => "medium"
    case IncidentPriority.Low      => "low"
  }

  def toAttribute: EventAttribute = "priority" := render()
}
object IncidentPriority {
  case object Critical extends IncidentPriority
  case object High     extends IncidentPriority
  case object Medium   extends IncidentPriority
  case object Low      extends IncidentPriority
}

final case class IncidentAggregationTagEventAttribute(key: IncidentAggregationTag, value: EventValue) {
  def toEventAttribute: EventAttribute = EventAttribute(s"aggregationTag.${key.value}", value)
}
object IncidentAggregationTagEventAttribute {
  def from[V](key: IncidentAggregationTag, value: V)(implicit ev: CanLog[V]): IncidentAggregationTagEventAttribute =
    IncidentAggregationTagEventAttribute(key, EventValue.from(value))
}
