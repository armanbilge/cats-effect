/*
 * Copyright 2020-2022 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect.unsafe

import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.atomic.AtomicBoolean

private final class SleepCallback private (
    val triggerTime: Long,
    private[this] var _callback: Right[Nothing, Unit] => Unit)
    extends AtomicBoolean(true)
    with Runnable {

  def callback(r: Right[Nothing, Unit]): Unit = {
    val cb = _callback
    println((cb, get()))
    if (cb != null) {
      cb(r)
    }
  }

  override def run(): Unit = {
    lazySet(false)
    _callback = null // avoid memory leaks
  }
}

private object SleepCallback {

  /**
   * Translated to Scala from:
   * https://github.com/openjdk/jdk/blob/04a806ec86a388b8de31d42f904c4321beb69e14/src/java.base/share/classes/java/util/concurrent/ScheduledThreadPoolExecutor.java#L527-L547
   */
  def create(
      delay: FiniteDuration,
      callback: Right[Nothing, Unit] => Unit,
      now: Long,
      sleepers: SleepersQueue): SleepCallback = {

    def overflowFree(delay: Long, now: Long): Long =
      if (sleepers.isEmpty) delay
      else {
        val head = sleepers.head()
        val headDelay = head.triggerTime - now
        if (headDelay < 0 && (delay - headDelay < 0))
          Long.MaxValue + headDelay
        else
          delay
      }

    val triggerTime = {
      val delayNanos = delay.toNanos

      if (delayNanos < (Long.MaxValue >> 1))
        now + delayNanos
      else
        now + overflowFree(delayNanos, now)
    }

    new SleepCallback(triggerTime, callback)
  }

  implicit val sleepCallbackReverseOrdering: Ordering[SleepCallback] =
    Ordering.fromLessThan(_.triggerTime - _.triggerTime > 0)
}
