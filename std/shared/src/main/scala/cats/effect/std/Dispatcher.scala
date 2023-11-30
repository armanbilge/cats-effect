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

package cats.effect.std

import cats.effect.kernel.{Async, Outcome, Resource}
import cats.effect.std.Dispatcher.parasiticEC
import cats.syntax.all._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

/**
 * A fiber-based supervisor utility for evaluating effects across an impure boundary. This is
 * useful when working with reactive interfaces that produce potentially many values (as opposed
 * to one), and for each value, some effect in `F` must be performed (like inserting each value
 * into a queue).
 *
 * [[Dispatcher]] is a kind of [[Supervisor]] and accordingly follows the same scoping and
 * lifecycle rules with respect to submitted effects.
 *
 * Performance note: all clients of a single [[Dispatcher]] instance will contend with each
 * other when submitting effects. However, [[Dispatcher]] instances are cheap to create and have
 * minimal overhead, so they can be allocated on-demand if necessary.
 *
 * Notably, [[Dispatcher]] replaces Effect and ConcurrentEffect from Cats Effect 2 while only
 * requiring an [[cats.effect.kernel.Async]] constraint.
 */
trait Dispatcher[F[_]] extends DispatcherPlatform[F] {

  /**
   * Submits an effect to be executed, returning a `Future` that holds the result of its
   * evaluation, along with a cancelation token that can be used to cancel the original effect.
   */
  def unsafeToFutureCancelable[A](fa: F[A]): (Future[A], () => Future[Unit])

  /**
   * Submits an effect to be executed, returning a `Future` that holds the result of its
   * evaluation.
   */
  def unsafeToFuture[A](fa: F[A]): Future[A] =
    unsafeToFutureCancelable(fa)._1

  /**
   * Submits an effect to be executed, returning a cancelation token that can be used to cancel
   * it.
   */
  def unsafeRunCancelable[A](fa: F[A]): () => Future[Unit] =
    unsafeToFutureCancelable(fa)._2

  /**
   * Submits an effect to be executed with fire-and-forget semantics.
   */
  def unsafeRunAndForget[A](fa: F[A]): Unit =
    unsafeToFuture(fa).onComplete {
      case Failure(ex) => ex.printStackTrace()
      case _ => ()
    }(parasiticEC)

  // package-private because it's just an internal utility which supports specific implementations
  // anyone who needs this type of thing should use unsafeToFuture and then onComplete
  private[std] def unsafeRunAsync[A](fa: F[A])(cb: Either[Throwable, A] => Unit): Unit =
    unsafeToFuture(fa).onComplete(t => cb(t.toEither))(parasiticEC)
}

object Dispatcher {

  private val parasiticEC: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable) = runnable.run()

    def reportFailure(t: Throwable) = t.printStackTrace()
  }

  private[this] val Cpus: Int = Runtime.getRuntime().availableProcessors()

  @deprecated(
    message =
      "use '.parallel' or '.sequential' instead; the former corresponds to the current semantics of '.apply'",
    since = "3.4.0")
  def apply[F[_]: Async]: Resource[F, Dispatcher[F]] = parallel[F](await = false)

  /**
   * Create a [[Dispatcher]] that can be used within a resource scope. Once the resource scope
   * exits, all active effects will be canceled, and attempts to submit new effects will throw
   * an exception.
   */
  def parallel[F[_]: Async]: Resource[F, Dispatcher[F]] =
    parallel[F](await = false)

  /**
   * Create a [[Dispatcher]] that can be used within a resource scope. Once the resource scope
   * exits, all active effects will be canceled, and attempts to submit new effects will throw
   * an exception.
   */
  def sequential[F[_]: Async]: Resource[F, Dispatcher[F]] =
    sequential[F](await = false)

  /**
   * Create a [[Dispatcher]] that can be used within a resource scope. Once the resource scope
   * exits, depending on the termination policy all active effects will be canceled or awaited,
   * and attempts to submit new effects will throw an exception.
   *
   * This corresponds to a pattern in which a single `Dispatcher` is being used by multiple
   * calling threads simultaneously, with complex (potentially long-running) actions submitted
   * for evaluation. In this mode, order of operation is not in any way guaranteed, and
   * execution of each submitted action has some unavoidable overhead due to the forking of a
   * new fiber for each action. This mode is most appropriate for scenarios in which a single
   * `Dispatcher` is being widely shared across the application, and where sequencing is not
   * assumed.
   *
   * The lifecycle of spawned fibers is managed by [[Supervisor]]. The termination policy can be
   * configured by the `await` parameter.
   *
   * @see
   *   [[Supervisor]] for the termination policy details
   *
   * @note
   *   if an effect that never completes, is evaluating by a `Dispatcher` with awaiting
   *   termination policy, the termination of the `Dispatcher` is indefinitely suspended
   *   {{{
   *   val io: IO[Unit] = // never completes
   *     Dispatcher.parallel[F](await = true).use { dispatcher =>
   *       dispatcher.unsafeRunAndForget(Concurrent[F].never)
   *       Concurrent[F].unit
   *     }
   *   }}}
   *
   * @param await
   *   the termination policy of the internal [[Supervisor]].
   *   - true - wait for the completion of the active fibers
   *   - false - cancel the active fibers
   */
  def parallel[F[_]: Async](await: Boolean): Resource[F, Dispatcher[F]] =
    Supervisor[F](await).flatMap { supervisor =>
      Resource
        .eval(F.delay(new Parallel(await, supervisor)))
        .flatTap(_.runWorker.background)
        .replicateA(4 * Cpus)
        .map(dispatchers => new Striped(dispatchers.toArray))
    }

  /**
   * Create a [[Dispatcher]] that can be used within a resource scope. Once the resource scope
   * exits, depending on the termination policy all active effects will be canceled or awaited,
   * and attempts to submit new effects will throw an exception.
   *
   * This corresponds to a [[Dispatcher]] mode in which submitted actions are evaluated strictly
   * in sequence (FIFO). In this mode, any actions submitted to
   * [[Dispatcher.unsafeRunAndForget]] are guaranteed to run in exactly the order submitted, and
   * subsequent actions will not start evaluation until previous actions are completed. This
   * avoids a significant amount of overhead associated with the [[Parallel]] mode and allows
   * callers to make assumptions around ordering, but the downside is that long-running actions
   * will starve subsequent actions, and all submitters must contend for a singular coordination
   * resource. Thus, this mode is most appropriate for cases where the actions are relatively
   * trivial (such as [[Queue.offer]]) ''and'' the `Dispatcher` in question is ''not'' shared
   * across multiple producers. To be clear, shared dispatchers in sequential mode will still
   * function correctly, but performance will be suboptimal due to single-point contention.
   *
   * @note
   *   if an effect that never completes, is evaluating by a `Dispatcher` with awaiting
   *   termination policy, the termination of the `Dispatcher` is indefinitely suspended
   *   {{{
   *   val io: IO[Unit] = // never completes
   *     Dispatcher.sequential[IO](await = true).use { dispatcher =>
   *       dispatcher.unsafeRunAndForget(IO.never)
   *       IO.unit
   *     }
   *   }}}
   *
   * @param await
   *   the termination policy.
   *   - true - wait for the completion of the active fiber
   *   - false - cancel the active fiber
   */
  def sequential[F[_]](await: Boolean)(implicit F: Async[F]): Resource[F, Dispatcher[F]] =
    Resource.eval(F.delay(new Sequential[F](await))).flatTap(_.runWorker.background)

  private final class Registration[F[_]](val action: F[Unit])
      extends AtomicReference[AnyRef](null)

  private final class Sequential[F[_]](await: Boolean)(implicit F: Async[F])
      extends AtomicReference[AnyRef](Queue.empty)
      with Dispatcher[F] {

    def unsafeToFutureCancelable[A](fa: F[A]) = {

      val promise = Promise[A]()
      val cancelPromise = Promise[Unit]()
      val registration = new Registration(
        fa.redeemWith[Unit](
          ex => F.delay(promise.failure(ex)),
          a => F.delay(promise.success(a))
        ).guarantee(F.delay(cancelPromise.success(())))
      )

      enqueue(registration)

      val cancel = { () =>
        if (registration.compareAndSet(null, cancelPromise))
          cancelPromise.future
        else
          registration.get().asInstanceOf[Promise[Unit]].future
      }

      (promise.future, cancel)
    }

    @tailrec
    private[this] def enqueue(registration: Registration[F]): Unit =
      get() match {
        case null =>
          throw new IllegalStateException("dispatcher already shutdown")
        case queue: Queue[Registration[F] @unchecked] =>
          if (!compareAndSet(queue, queue :+ registration))
            enqueue(registration)
        case _cb =>
          val cb = _cb.asInstanceOf[Either[Throwable, Registration[F]] => Unit]
          if (compareAndSet(cb, Queue.empty))
            cb(Right(registration))
          else
            enqueue(registration)
      }

    def runWorker: F[Unit] =
      F.onCancel(
        ???,
        ???
      )

    private[this] def workerLoop: F[Unit] = F.uncancelable { poll =>
      poll(nextRegistration).flatMap { registration =>
        F.delay(registration.get().asInstanceOf[Promise[Unit]]).flatMap {
          case null => // not canceled
            poll(registration.action)
          case canceledPromise => // acknowledge cancelation
            F.delay(canceledPromise.success(()))
        }
      }
    }.foreverM

    private[this] def nextRegistration: F[Registration[F]] =
      F.asyncCheckAttempt(cb => F.delay(getNextOrInstallCallback(cb)))

    @tailrec
    private[this] def getNextOrInstallCallback(cb: Either[Throwable, Registration[F]])
        : Either[Option[F[Unit]], List[Registration[F]]] = {

      val registrations = get().asInstanceOf[Queue[Registration[F]]]

      if (registrations.isEmpty) {
        if (compareAndSet(registrations, cb))
          return Left(F.delay { compareAndSet(cb, null); () })
      } else {
        if (compareAndSet(registrations, registrations.tail))
          return Right(registrations.head)
      }

      getNextOrInstallCallback(cb)
    }
  }

  private final class Parallel[F[_]](await: Boolean, supervisor: Supervisor[F])(
      implicit F: Async[F]
  ) extends AtomicReference[AnyRef](Nil)
      with Dispatcher[F] {

    def unsafeToFutureCancelable[A](fa: F[A]) = {

      val promise = Promise[A]()
      val registration = new Registration(
        fa.redeemWith[Unit](
          ex => F.delay(promise.failure(ex)),
          a => F.delay(promise.success(a))
        )
      )

      enqueue(registration)

      val cancel = { () =>
        def go(): Future[Unit] = {
          registration.get() match {
            case null =>
              val p = Promise[Unit]()
              if (compareAndSet(null, p))
                p.future
              else
                go()
            case p: Promise[Unit @unchecked] =>
              p.future
            case action =>
              unsafeToFuture(action.asInstanceOf[F[Unit]])
          }
        }

        go()
      }

      (promise.future, cancel)
    }

    @tailrec
    private[this] def enqueue(registration: Registration[F]): Unit =
      get() match {
        case null => throw new IllegalStateException("dispatcher already shutdown")
        case registrations: List[Registration[F] @unchecked] =>
          if (!compareAndSet(registrations, registration :: registrations))
            enqueue(registration)
        case _cb =>
          val cb = _cb.asInstanceOf[Either[Throwable, List[Registration[F]]] => Unit]
          if (compareAndSet(cb, Nil))
            cb(Right(registration :: Nil))
          else
            enqueue(registration)
      }

    def runWorker: F[Unit] = F.uncancelable { poll =>
      F.onCancel(
        poll(nextRegistrations).flatMap { registrations =>
          // todo, this could be cancelable
          registrations.foldMap(runAction(_))
        },
        F.delay(getAndSet(null)).flatMap {
          case registrations: List[Registration[F] @unchecked] =>
            if (await) // make sure that everything is submitted
              registrations.foldMap(runAction(_))
            else
              F.delay {
                registrations.foreach { registration =>
                  registration.get() match {
                    case null =>
                      if (!compareAndSet(null, Promise.successful(())))
                        registration.get().asInstanceOf[Promise[Unit]].success(())
                    case p => p.asInstanceOf[Promise[Unit]].success(())
                  }
                }
              }
        }
      )
    }

    // must be uncancelable
    private[this] def runAction(registration: Registration[F]): F[Unit] =
      F.delay(registration.get().asInstanceOf[Promise[Unit]]).flatMap {
        case null => // not canceled
          supervisor.supervise(registration.action).flatMap { fiber =>
            // double-check and cancel if necessary
            F.delay(registration.compareAndSet(null, fiber.cancel)).flatMap {
              case true => F.unit
              case false =>
                F.delay(registration.get().asInstanceOf[Promise[Unit]]).flatMap { p =>
                  // don't block the worker
                  supervisor.supervise(fiber.cancel.guarantee(F.delay(p.success(()))))
                }
            }
          }
        case canceledPromise => // acknowledge cancelation
          F.delay(canceledPromise.success(()))
      }

    private[this] def nextRegistrations: F[List[Registration[F]]] =
      F.asyncCheckAttempt(cb => F.delay(getBatchOrInstallCallback(cb)))

    @tailrec
    private[this] def getBatchOrInstallCallback(cb: Either[Throwable, List[Registration[F]]])
        : Either[Option[F[Unit]], List[Registration[F]]] = {
      val registrations = get().asInstanceOf[List[Registration[F]]]
      if (registrations.isEmpty) {
        if (compareAndSet(registrations, cb))
          return Left(Some(F.delay { compareAndSet(cb, null); () }))
      } else {
        if (compareAndSet(registrations, Nil))
          return Right(registrations.reverse)
      }
      getBatchOrInstallCallback(cb)
    }

  }

  private final class Striped[F[_]](dispatchers: Array[Parallel[F]]) extends Dispatcher[F] {
    def unsafeToFutureCancelable[A](fa: F[A]) = {
      val random = ThreadLocalRandom.current()
      val i = random.nextInt(dispatchers.length)
      dispatchers(i).unsafeToFutureCancelable(fa)
    }
  }

  private[this] def apply[F[_]](mode: Mode, await: Boolean)(
      implicit F: Async[F]): Resource[F, Dispatcher[F]] = ???

  private object Mode {
    case object Parallel extends Mode
    case object Sequential extends Mode
  }
}
