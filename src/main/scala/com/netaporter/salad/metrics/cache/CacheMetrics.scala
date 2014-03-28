package com.netaporter.salad.metrics.cache

import spray.caching.{ ExpiringLruCache, LruCache, Cache }
import scala.concurrent.{ ExecutionContext, Future }
import java.util.concurrent.atomic.AtomicLong
import akka.actor.Actor
import com.netaporter.salad.metrics.actor.factory.MetricsActorFactory
import com.netaporter.salad.metrics.messages.MetricEventMessage.GaugeEvent
import com.twitter.jsr166e.LongAdder
import scala.concurrent.duration.Duration

trait CacheMetrics {
  this: Actor =>

  protected val eventActor = MetricsActorFactory.eventActor()(context)

  def LruCacheWithMetrics[V](metricsName: String,
    maxCapacity: Int = 500,
    initialCapacity: Int = 16,
    timeToLive: Duration = Duration.Inf,
    timeToIdle: Duration = Duration.Inf): Cache[V] =
    new MetricsCache(LruCache(maxCapacity, initialCapacity, timeToLive, timeToIdle), metricsName, maxCapacity)

  class MetricsCache[V](delegate: Cache[V], metricsName: String, maxCapacity: Int) extends Cache[V] {

    val total = new LongAdder
    val misses = new LongAdder
    def hits = total.longValue - misses.longValue

    def hitRatio =
      if (total.longValue == 0l) 0.0
      else hits.toDouble / total.longValue.toDouble

    /**
     * How full the cache is. Percentage between 0.0 and 1.0
     */
    def usagePercent =
      delegate.size.toDouble / maxCapacity.toDouble

    eventActor ! GaugeEvent(metricsName + ".hit-ratio", hitRatio _)
    eventActor ! GaugeEvent(metricsName + ".usage-percent", usagePercent _)

    /**
     * Returns either the cached Future for the given key or evaluates the given value generating
     * function producing a `Future[V]`.
     */
    def apply(key: Any, genValue: () ⇒ Future[V])(implicit ec: ExecutionContext): Future[V] = {
      total.increment()

      val incOnMiss = () => {
        misses.increment()
        genValue.apply()
      }

      delegate.apply(key, incOnMiss)
    }

    /**
     * Retrieves the future instance that is currently in the cache for the given key.
     * Returns None if the key has no corresponding cache entry.
     */
    def get(key: Any) = {
      total.increment()

      val res = delegate.get(key)

      if (res.isEmpty) {
        misses.increment()
      }

      res
    }

    /**
     * Removes the cache item for the given key. Returns the removed item if it was found (and removed).
     */
    def remove(key: Any) = delegate.remove(key)
    def clear() = delegate.clear()
    def size = delegate.size
  }
}
