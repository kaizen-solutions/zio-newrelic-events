package io.kaizensolutions.event.logger

import io.kaizensolutions.event.logger.incidents.*
import zio.*

object Example extends ZIOAppDefault {
  val run: ZIO[Any, Any, Any] = {
    val program: RIO[NewRelicEventApi, Unit] =
      for {
        value <- Random.nextInt
        str    = s"Hello$value"
        el    <- ZIO.service[NewRelicEventApi]
        incidentAspect = el.incident(
                           IncidentState.Trigger("sourceSystem", "exampleTitle"),
                           IncidentPriority.Medium,
                           Set(
                             aggregationTag("exampleKey")  := "exampleValue",
                             aggregationTag("exampleKey2") := 123
                           ),
                           "context" := "hello"
                         )
        eventAspect = el.event("example-event", "int" := value, "string" := str)
        _          <- Console.printLine(str) @@ incidentAspect @@ eventAspect
        _          <- el.queueSizeMetric.value.debug("Queue Size: ")
      } yield ()

    program
      .repeat(Schedule.spaced(1.second))
      .provide(
        NewRelicEventApi.live,
        ZLayer.succeed(
          NewRelicEventLoggerConfig(
            new ServiceName("example-event-logger"),
            new AccountId("<Your Account Here>"),
            NewRelicEndpoint.US,
            new NewRelicIngestLicenseKey("<Your Key Here>")
          )
        )
      )
  }
}
