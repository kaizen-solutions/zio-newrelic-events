package io.kaizensolutions.event.logger

import io.kaizensolutions.event.logger.incidents.*
import io.kaizensolutions.event.logger.internal.{LogEvent, UnixTimestamp}
import sttp.client3.*
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.{MediaType, StatusCode}
import zio.*
import zio.metrics.Metric

import scala.annotation.nowarn

final class NewRelicEventApi(
  config: NewRelicEventLoggerConfig,
  client: SttpBackend[Task, Any],
  pending: Queue[LogEvent]
) {
  private val serviceNameAttr = EventAttribute.from("serviceName", config.name.value)

  val queueSizeMetric: Metric.Gauge[Int] =
    Metric
      .gauge(s"new_relic_${config.name}_event_logger_queue_size")
      .contramap[Int](_.toDouble)

  private val newRelicRequest =
    basicRequest
      .post(config.endpoint.uri(config.accountId))
      .header("Api-Key", config.licenseKey.value)
      .header("Content-Encoding", "gzip")
      .contentType(MediaType.ApplicationJson)

  @nowarn private val reportQueueMetrics: UIO[Any] =
    (pending.size @@ queueSizeMetric).repeat(config.reportSchedule)

  private val sendContinuously: UIO[Nothing] = {
    val sendStep =
      pending
        .takeUpTo(config.batchSize)
        .flatMap(sendEvents)

    sendStep
      .retry(config.retrySchedule)
      .ignoreLogged
      .delay(config.batchDelay)
      .forever
  }

  def sendEvent(eventType: String, attributes: Set[EventAttribute], timestamp: Option[UnixTimestamp] = None): UIO[Unit] = {
    val adjustedEventType = eventType.replace("-", "_")
    pending.offer(LogEvent(adjustedEventType, attributes + serviceNameAttr, timestamp)).unit
  }

  def sendIncident(
    state: IncidentState,
    priority: IncidentPriority,
    aggregationTags: Set[IncidentAggregationTagEventAttribute],
    attributes: Set[EventAttribute],
    timestamp: Option[UnixTimestamp] = None
  ): UIO[Unit] = {
    val allAttributes =
      attributes ++
        aggregationTags.map(_.toEventAttribute) ++
        state.toAttributes +
        priority.toAttribute

    sendEvent(Incident, allAttributes, timestamp)
  }

  def event(
    eventType: String,
    timestamp: Option[UnixTimestamp] = None,
    attributes: Set[EventAttribute]
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R >: Nothing <: Any, E >: Nothing <: Any, A >: Nothing <: Any](zio: ZIO[R, E, A])(implicit
        trace: Trace
      ): ZIO[R, E, A] = zio.zipLeft(sendEvent(eventType, attributes, timestamp))
    }

  def event(
    eventType: String,
    attributes: EventAttribute*
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R >: Nothing <: Any, E >: Nothing <: Any, A >: Nothing <: Any](zio: ZIO[R, E, A])(implicit
        trace: Trace
      ): ZIO[R, E, A] = zio.zipLeft(sendEvent(eventType, attributes.toSet, None))
    }

  def event(
    eventType: String,
    timestamp: UnixTimestamp,
    attributes: EventAttribute*
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R >: Nothing <: Any, E >: Nothing <: Any, A >: Nothing <: Any](zio: ZIO[R, E, A])(implicit
        trace: Trace
      ): ZIO[R, E, A] = zio.zipLeft(sendEvent(eventType, attributes.toSet, Option(timestamp)))
    }

  def incident(
    timestamp: Option[UnixTimestamp],
    state: IncidentState,
    priority: IncidentPriority,
    aggregationTags: Set[IncidentAggregationTagEventAttribute],
    attributes: EventAttribute*
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] = new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {

    override def apply[R >: Nothing <: Any, E >: Nothing <: Any, A >: Nothing <: Any](zio: ZIO[R, E, A])(implicit
      trace: Trace
    ): ZIO[R, E, A] = zio.zipLeft(sendIncident(state, priority, aggregationTags, attributes.toSet, timestamp))
  }

  def incident(
    state: IncidentState,
    priority: IncidentPriority,
    aggregationTags: Set[IncidentAggregationTagEventAttribute],
    attributes: EventAttribute*
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    incident(None, state, priority, aggregationTags, attributes *)

  private def sendEvents(events: Chunk[LogEvent]): Task[StatusCode] =
    newRelicRequest
      .body(LogEvent.gzip(events))
      .send(client)
      .tapErrorCause(error => ZIO.logErrorCause(s"Failed to send events to New Relic", error))
      .flatMap { response =>
        if (response.code.isSuccess) ZIO.succeed(response.code)
        else
          ZIO
            .logError(s"Failed to send events to New Relic: ${response.body.fold(identity, identity)}, Status Code ${response.code}")
            .as(response.code)
      }
}
object NewRelicEventApi {
  def make(config: NewRelicEventLoggerConfig, client: SttpBackend[Task, Any], queue: Queue[LogEvent]): NewRelicEventApi =
    new NewRelicEventApi(config, client, queue)

  val live: RLayer[NewRelicEventLoggerConfig, NewRelicEventApi] =
    ZLayer.scoped(
      for {
        config <- ZIO.service[NewRelicEventLoggerConfig]
        client <- HttpClientZioBackend()
        queue  <- Queue.sliding[LogEvent](config.queueSize)
        logger  = NewRelicEventApi.make(config, client, queue)
        _      <- logger.sendContinuously.forkScoped
        _      <- logger.reportQueueMetrics.forkScoped
      } yield logger
    )
  val customized: RLayer[SttpBackendOptions & NewRelicEventLoggerConfig, NewRelicEventApi] =
    ZLayer.scoped(
      for {
        config  <- ZIO.service[NewRelicEventLoggerConfig]
        options <- ZIO.service[SttpBackendOptions]
        backend <- HttpClientZioBackend(options)
        queue   <- Queue.sliding[LogEvent](config.queueSize)
        logger   = NewRelicEventApi.make(config, backend, queue)
        _       <- logger.sendContinuously.forkScoped
        _       <- logger.reportQueueMetrics.forkScoped
      } yield logger
    )

  val layer: RLayer[SttpBackend[Task, Any] & NewRelicEventLoggerConfig, NewRelicEventApi] =
    ZLayer.fromZIO(
      for {
        config <- ZIO.service[NewRelicEventLoggerConfig]
        client <- ZIO.service[SttpBackend[Task, Any]]
        queue  <- Queue.sliding[LogEvent](config.queueSize)
      } yield NewRelicEventApi.make(config, client, queue)
    )
}
