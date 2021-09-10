/*
 * Copyright 2020-2021 Typelevel
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

package cats.effect
package unsafe

import java.util.PriorityQueue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

private[effect] final class EventLoop extends ExecutionContext with Scheduler {

  override def execute(runnable: Runnable): Unit = {
    queue.offer((nowMillis(), runnable))
    runLoop()
  }

  override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()

  override def sleep(delay: FiniteDuration, task: Runnable): Runnable = {
    val e = (nowMillis(), task)
    queue.offer(e)
    runLoop()
    () => { queue.remove(e); () }
  }

  override def nowMillis(): Long = System.currentTimeMillis()

  override def monotonicNanos(): Long = System.nanoTime()

  private[this] val queue: PriorityQueue[(Long, Runnable)] =
    new PriorityQueue[(Long, Runnable)](11, (x, y) => java.lang.Long.compare(x._1, y._1))

  private[this] var runLooping = false
  private[this] def runLoop(): Unit =
    if (!runLooping) {
      runLooping = true
      while (!queue.isEmpty()) {
        val now = nowMillis()
        if (now >= queue.peek()._1)
          queue.poll()._2.run()
      }
      runLooping = false
    }

}
