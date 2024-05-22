# New Relic Events For ZIO

![Maven Central Version](https://img.shields.io/maven-central/v/io.kaizen-solutions/zio-newrelic-events_3)
[![Continuous Integration](https://github.com/kaizen-solutions/zio-newrelic-events/actions/workflows/ci.yml/badge.svg)](https://github.com/kaizen-solutions/zio-newrelic-events/actions/workflows/ci.yml)

This library allows you to [send custom events to New Relic](https://docs.newrelic.com/docs/data-apis/ingest-apis/event-api/introduction-event-api/#overview) 
utilizing [ZIO](https://zio.dev).

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "io.kaizen-solutions" %% "zio-newrelic-events" % "<see badge for latest version>"
```

## Usage

In order to use the library, you need to access the `NewRelicEventApi` which is provided by the `NewRelicEventApi.live` 
layer and its variants. You can use `NewRelicEventApi#sendEvent(...)` or `NewRelicEventApi#sendIncident(...)` to send events 
to New Relic. However, we recommend using aspects to send events & incidents instead as they provide better ergonomics
in you ZIO workflows. See an example below.

## Example

```scala
import zio.*
import io.kaizensolutions.event.logger.*

object Example extends ZIOAppDefault {
  val run: ZIO[Any, Any, Any] = {
    val program: RIO[NewRelicEventApi, Unit] =
      for {
        nr <- ZIO.service[NewRelicEventApi]
        value <- Random.nextInt
        _ <- Console.printLine("Hello") @@ nr.event(
          "example-event",
          "int" := value,
          "string" := s"Hello$value"
        )
      } yield ()

    program
      .repeat(Schedule.spaced(1.second))
      .provide(
        NewRelicEventApi.live,
        ZLayer.succeed( // pull from config
          NewRelicEventLoggerConfig(
            new ServiceName("example-event-logger"),
            new AccountId("<YourAccountIdHere>"),
            NewRelicEndpoint.US,
            new NewRelicIngestLicenseKey("<YourKeyHere>")
          )
        )
      )
  }
}
```

See the tests folder for another example that also uses aspects for incidents.

## Architecture

This library uses a sliding queue to batch events and send them to New Relic in a single request. 
This is done to reduce the number of requests made to the New Relic API. Retries are also handled in the case of a 
failure and policies are configurable.

## Additional information

This service spins up a metric `new_relic_{YOUR_SERVICE_NAME}_event_logger_queue_size` that can be used to monitor the 
size of the Queue.
