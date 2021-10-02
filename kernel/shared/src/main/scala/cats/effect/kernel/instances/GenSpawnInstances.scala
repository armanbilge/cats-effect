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

package cats.effect.kernel.instances

import cats.{~>, Align, Applicative, CommutativeApplicative, Eval, Functor, Monad, Parallel}
import cats.data.Ior
import cats.implicits._
import cats.effect.kernel.{GenSpawn, Outcome, ParallelF}

trait GenSpawnInstances {

  implicit def parallelForGenSpawn[M[_], E](
      implicit M: GenSpawn[M, E]): Parallel.Aux[M, ParallelF[M, *]] =
    new Parallel[M] {
      type F[A] = ParallelF[M, A]

      def applicative: Applicative[F] = commutativeApplicativeForParallelF[M, E]

      def monad: Monad[M] = M

      def sequential: F ~> M =
        new (F ~> M) {
          def apply[A](fa: F[A]): M[A] = ParallelF.value[M, A](fa)
        }

      def parallel: M ~> F =
        new (M ~> F) {
          def apply[A](ma: M[A]): F[A] = ParallelF[M, A](ma)
        }

    }

  implicit def commutativeApplicativeForParallelF[F[_], E](
      implicit F: GenSpawn[F, E]): CommutativeApplicative[ParallelF[F, *]] =
    new CommutativeApplicative[ParallelF[F, *]] {

      final override def pure[A](a: A): ParallelF[F, A] = ParallelF(F.pure(a))

      final override def map2[A, B, Z](fa: ParallelF[F, A], fb: ParallelF[F, B])(
          f: (A, B) => Z): ParallelF[F, Z] =
        ParallelF(
          F.uncancelable { poll =>
            for {
              fiberA <- F.start(ParallelF.value(fa))
              fiberB <- F.start(ParallelF.value(fb))

              // start a pair of supervisors to ensure that the opposite is canceled on error
              _ <- F start {
                fiberB.join flatMap {
                  case Outcome.Succeeded(_) => F.unit
                  case _ => fiberA.cancel
                }
              }

              _ <- F start {
                fiberA.join flatMap {
                  case Outcome.Succeeded(_) => F.unit
                  case _ => fiberB.cancel
                }
              }

              a <- F
                .onCancel(poll(fiberA.join), F.both(fiberA.cancel, fiberB.cancel).void)
                .flatMap[A] {
                  case Outcome.Succeeded(fa) =>
                    fa

                  case Outcome.Errored(e) =>
                    fiberB.cancel *> F.raiseError(e)

                  case Outcome.Canceled() =>
                    fiberB.cancel *> poll {
                      fiberB.join flatMap {
                        case Outcome.Succeeded(_) | Outcome.Canceled() =>
                          F.canceled *> F.never
                        case Outcome.Errored(e) =>
                          F.raiseError(e)
                      }
                    }
                }

              z <- F.onCancel(poll(fiberB.join), fiberB.cancel).flatMap[Z] {
                case Outcome.Succeeded(fb) =>
                  fb.map(b => f(a, b))

                case Outcome.Errored(e) =>
                  F.raiseError(e)

                case Outcome.Canceled() =>
                  poll {
                    fiberA.join flatMap {
                      case Outcome.Succeeded(_) | Outcome.Canceled() =>
                        F.canceled *> F.never
                      case Outcome.Errored(e) =>
                        F.raiseError(e)
                    }
                  }
              }
            } yield z
          }
        )

      // final override def map2[A, B, Z](fa: ParallelF[F, A], fb: ParallelF[F, B])(
      //     f: (A, B) => Z): ParallelF[F, Z] =
      //   ParallelF {
      //     val fz = F.uncancelable { poll =>
      //       for {
      //         fiberA <- F.start(ParallelF.value(fa))
      //         _ = println(s"started fiberA aka ${fiberA.hashCode()}")
      //         fiberB <- F.start(ParallelF.value(fb))
      //         _ = println(s"started fiberB aka ${fiberB.hashCode()}")

      //         // start a pair of supervisors to ensure that the opposite is canceled on error
      //         _ <- F start {
      //           fiberB.join flatMap {
      //             case Outcome.Succeeded(fb) => 
      //               fb.map(x => println(s"supervisor joined fiber b aka ${fiberB.hashCode()} who succeeded with: $x"))
      //             case x => fiberA.cancel.as(println(s"supervisor joined fiber b aka ${fiberB.hashCode()} who failed with $x"))
      //           }
      //         }

      //         _ <- F start {
      //           fiberA.join flatMap {
      //             case Outcome.Succeeded(fa) =>
      //               fa.map(x => println(s"supervisor joined fiber a aka ${fiberA.hashCode()} who succeeded with: $x"))
      //             case x => fiberB.cancel.as(println(s"supervisor joined fiber a aka ${fiberA.hashCode()} who failed with $x"))
      //           }
      //         }

      //         a <- F
      //           .onCancel(poll(fiberA.join), F.both(fiberA.cancel.as(println(s"1st onCancel cancelling fiberA aka ${fiberA.hashCode()}")), fiberB.cancel.as(println(s"1st onCancel cancelling fiberB aka ${fiberB.hashCode()}"))).void)
      //           .flatMap[A] {
      //             case Outcome.Succeeded(fa) =>
      //               println(s"joined with fiber a aka ${fiberA.hashCode()} who succeeded")
      //               fa

      //             case Outcome.Errored(e) =>
      //               println(s"joined with fiber a aka ${fiberA.hashCode()} who errored with $e")
      //               fiberB.cancel *> F.raiseError(e)

      //             case Outcome.Canceled() =>
      //               println(s"joined with fiber a aka ${fiberA.hashCode()} who was cancelled")
      //               fiberB.cancel *> poll {
      //                 fiberB.join flatMap {
      //                   case Outcome.Succeeded(_) | Outcome.Canceled() =>
      //                     println(s"joined with fiber b aka ${fiberB.hashCode()} who succeeded or cancelled, but self-cancelling b/c fiber a aka ${fiberA.hashCode()} canceled")
      //                     F.canceled *> F.never
      //                   case Outcome.Errored(e) =>
      //                     println(s"joined with fiber b aka ${fiberB.hashCode()} who errored with $e, raising despite fiber a's aka ${fiberA.hashCode()} cancellation")
      //                     F.raiseError(e)
      //                 }
      //               }
      //           }

      //         _ = println(s"I joined with fiberA aka ${fiberA.hashCode()} and got: $a")

      //         z <- F.onCancel(poll(fiberB.join), fiberB.cancel.as(println("2nd onCancel cancelling fiber b"))).flatMap[Z] {
      //           case Outcome.Succeeded(fb) =>
      //             fb.map(b => {
      //               val fab = f(a, b)
      //               println(s"successfully map2ed: $fab")
      //               fab
      //             })

      //           case Outcome.Errored(e) =>
      //             println(s"fiber a succeeded with $a and joined fiber b which failed with $e")
      //             F.raiseError(e)

      //           case Outcome.Canceled() =>
      //             println(s"fiber a succeeded with $a and joined fiber b which was cancelled")
      //             poll {
      //               fiberA.join flatMap {
      //                 case Outcome.Succeeded(_) | Outcome.Canceled() =>
      //                   F.canceled *> F.never
      //                 case Outcome.Errored(e) =>
      //                   F.raiseError(e)
      //               }
      //             }
      //         }
      //       } yield z
      //     }
      //     fz.attempt.map { z =>
      //       println(s"Attempted map2 and got: $z, now I will rethrow it")
      //       z
      //     }.rethrow
      //   }

      final override def map2Eval[A, B, Z](fa: ParallelF[F, A], fb: Eval[ParallelF[F, B]])(
          f: (A, B) => Z): Eval[ParallelF[F, Z]] =
        fb.map(map2(fa, _)(f))

      final override def ap[A, B](ff: ParallelF[F, A => B])(
          fa: ParallelF[F, A]): ParallelF[F, B] =
        product(ff, fa).map { case (ff, fa) => ff(fa) }

      final override def product[A, B](
          fa: ParallelF[F, A],
          fb: ParallelF[F, B]): ParallelF[F, (A, B)] = {
        map2(fa, fb)((_, _))
      }

      final override def map[A, B](fa: ParallelF[F, A])(f: A => B): ParallelF[F, B] =
        ParallelF(ParallelF.value(fa).map(f))

      final override def unit: ParallelF[F, Unit] =
        ParallelF(F.unit)
    }

  implicit def alignForParallelF[F[_], E](implicit F: GenSpawn[F, E]): Align[ParallelF[F, *]] =
    new Align[ParallelF[F, *]] {

      override def functor: Functor[ParallelF[F, *]] = commutativeApplicativeForParallelF[F, E]

      override def align[A, B](
          fa: ParallelF[F, A],
          fb: ParallelF[F, B]): ParallelF[F, Ior[A, B]] =
        alignWith(fa, fb)(identity)

      override def alignWith[A, B, C](fa: ParallelF[F, A], fb: ParallelF[F, B])(
          f: Ior[A, B] => C): ParallelF[F, C] =
        ParallelF(
          (ParallelF.value(fa).attempt, ParallelF.value(fb).attempt)
            .parMapN((ea, eb) => catsStdInstancesForEither.alignWith(ea, eb)(f))
            .flatMap(F.fromEither)
        )

    }
}
