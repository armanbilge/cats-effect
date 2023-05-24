/*
 * Copyright 2020-2023 Typelevel
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

import cats.effect.tracing.TracingConstants

import org.scalajs.macrotaskexecutor.MacrotaskExecutor

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.scalajs.{js, LinkingInfo}
import scala.util.control.NonFatal

/**
 * An `ExecutionContext` that improves throughput by providing a method to `schedule` fibers to
 * execute in batches, instead of one task per event loop iteration. This optimization targets
 * the typical scenario where a UI or I/O event handler starts/resumes a small number of
 * short-lived fibers and then yields to the event loop.
 *
 * This `ExecutionContext` also maintains a fiber bag in development mode to enable fiber dumps.
 *
 * @param batchSize
 *   the maximum number of batched runnables to execute before yielding to the event loop
 */
private[effect] final class BatchingMacrotaskExecutor(
    batchSize: Int,
    reportFailure0: Throwable => Unit
) extends ExecutionContextExecutor {

  private[this] val queueMicrotask: js.Function1[js.Function0[Any], Any] =
    if (js.typeOf(js.Dynamic.global.queueMicrotask) == "function")
      js.Dynamic.global.queueMicrotask.asInstanceOf[js.Function1[js.Function0[Any], Any]]
    else {
      val resolved = js.Dynamic.global.Promise.resolved(())
      task => resolved.`then`(task)
    }

  /**
   * Whether the `executeBatchTask` needs to be rescheduled
   */
  private[this] var needsReschedule = true
  private[this] var currentBatch = new JSArrayQueue[Runnable]
  private[this] var nextBatch = new JSArrayQueue[Runnable]

  private[this] val executeBatchTaskRunnable = new Runnable {

    def run() = {
      if (!nextBatch.isEmpty()) {
        // make the next batch the current batch
        val tmp = currentBatch
        currentBatch = nextBatch
        nextBatch = tmp
      }

      // do up to batchSize tasks
      var i = 0
      while (i < batchSize && !currentBatch.isEmpty()) {
        val fiber = currentBatch.take()

        unmonitor(fiber)

        try fiber.run()
        catch {
          case t if NonFatal(t) => reportFailure(t)
          case t: Throwable => IOFiber.onFatalFailure(t)
        }

        i += 1
      }

      if (!currentBatch.isEmpty() || !nextBatch.isEmpty())
        // we'll be right back after this (post) message
        MacrotaskExecutor.execute(this)
      else // the batch task will need to be rescheduled when more fibers arrive
        needsReschedule = true

      // yield to the event loop
    }
  }

  private[this] val executeBatchTaskJSFunction: js.Function0[Any] =
    () => executeBatchTaskRunnable.run()

  /**
   * Schedule the `runnable` for the next batch, which will execute in the next iteration of the
   * event loop.
   */
  def execute(runnable: Runnable): Unit = {
    monitor(runnable)

    nextBatch.offer(runnable)

    if (needsReschedule) {
      needsReschedule = false
      MacrotaskExecutor.execute(executeBatchTaskRunnable)
      ()
    }
  }

  /**
   * Schedule the `fiber` for the next available batch. This is often the currently executing
   * batch.
   */
  def schedule(fiber: Runnable): Unit = {
    monitor(fiber)

    currentBatch.offer(fiber)

    if (needsReschedule) {
      needsReschedule = false
      // start executing the batch immediately after the currently running task suspends
      // this is safe b/c `needsReschedule` is set to `true` only upon yielding to the event loop
      queueMicrotask(executeBatchTaskJSFunction)
    }

    ()
  }

  def reportFailure(t: Throwable): Unit = reportFailure0(t)

  def liveTraces(): Map[IOFiber[_], Trace] =
    fiberBag.iterator.filterNot(_.isDone).map(f => f -> f.captureTrace()).toMap

  @inline private[this] def monitor(fiber: Runnable): Unit =
    if (LinkingInfo.developmentMode)
      if ((fiberBag ne null) && fiber.isInstanceOf[IOFiber[_]])
        fiberBag += fiber.asInstanceOf[IOFiber[_]]

  @inline private[this] def unmonitor(fiber: Runnable): Unit =
    if (LinkingInfo.developmentMode)
      if ((fiberBag ne null) && fiber.isInstanceOf[IOFiber[_]])
        fiberBag -= fiber.asInstanceOf[IOFiber[_]]

  private[this] val fiberBag =
    if (LinkingInfo.developmentMode)
      if (TracingConstants.isStackTracing && FiberMonitor.weakRefsAvailable)
        mutable.Set.empty[IOFiber[_]]
      else
        null
    else
      null

}
