package io.kaizensolutions.event

import io.kaizensolutions.event.logger.incidents.IncidentAggregationTag
import io.kaizensolutions.event.logger.internal.*

package object logger {
  implicit class EventLoggerSyntax(val key: String) extends AnyVal {

    /**
     * "a" := "b" becomes EventAttribute("a", EventValue.Str("b"))
     * @param value
     *   the value to log
     * @param ev
     *   the evidence that the value can be logged (i.e. needs to have a CanLog
     *   instance)
     * @tparam V
     *   the type of the value to log
     * @return
     */
    def :=[V](value: V)(implicit ev: CanLog[V]): EventAttribute = EventAttribute.from(key, value)
  }

  def aggregationTag(key: String) = new IncidentAggregationTag(key)

  val Incident = "NrAiIncidentExternal"
}
