package io.kaizensolutions.event.logger

import sttp.client3.*
import sttp.model.Uri
import zio.*

final class AccountId(val value: String)                extends AnyVal
final class ServiceName(val value: String)              extends AnyVal
final class NewRelicIngestLicenseKey(val value: String) extends AnyVal

sealed trait NewRelicEndpoint { self =>
  def uri(accountId: AccountId) = self match {
    case NewRelicEndpoint.US          => uri"https://insights-collector.newrelic.com/v1/accounts/${accountId.value}/events"
    case NewRelicEndpoint.EU          => uri"https://insights-collector.eu01.nr-data.net/v1/accounts/${accountId.value}/events"
    case NewRelicEndpoint.Custom(url) => url.addPath("v1", "accounts", accountId.value, "events")
  }
}
object NewRelicEndpoint {
  case object US              extends NewRelicEndpoint
  case object EU              extends NewRelicEndpoint
  case class Custom(url: Uri) extends NewRelicEndpoint
}

final case class NewRelicEventLoggerConfig(
  name: ServiceName,
  accountId: AccountId,
  endpoint: NewRelicEndpoint,
  licenseKey: NewRelicIngestLicenseKey,
  queueSize: Int = 1024,
  batchSize: Int = 64,
  retrySchedule: Schedule[Any, Throwable, Throwable] = Schedule
    .linear(100.millis)
    .whileOutput(_ <= 5.seconds) *> Schedule.identity,
  reportSchedule: Schedule[Any, Any, Any] = Schedule.fixed(15.seconds),
  batchDelay: Duration = 1.second
)
