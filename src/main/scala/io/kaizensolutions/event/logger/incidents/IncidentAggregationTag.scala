package io.kaizensolutions.event.logger.incidents

import io.kaizensolutions.event.logger.internal.CanLog

final class IncidentAggregationTag(val value: String) extends AnyVal { self =>
  def :=[V](value: V)(implicit ev: CanLog[V]): IncidentAggregationTagEventAttribute =
    IncidentAggregationTagEventAttribute.from(self, value)
}
