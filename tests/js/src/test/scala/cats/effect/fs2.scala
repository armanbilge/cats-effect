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

import cats.effect.std.Semaphore
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.SignallingRef

import scala.concurrent.duration._

class Fs2Spec extends BaseSpec {

  "Fs2" should {
    "work" in real {
      val s = Stream.range(0, 100)
      val n = 3 // for some reason JS is running evalFilterAsync with only 2 concurrent level

      (Semaphore[IO](n.toLong), SignallingRef[IO, Int](0))
        .tupled
        .flatMap {
          case (sem, sig) =>
            val tested = s.covary[IO].evalFilterAsync(n) { _ =>
              val ensureAcquired =
                sem
                  .tryAcquire
                  .ifM(
                    IO.unit,
                    IO.raiseError(new Throwable("Couldn't acquire permit"))
                  )

              ensureAcquired
                .bracket(_ =>
                  sig.update(_ + 1).bracket(_ => IO.sleep(10.millis))(_ => sig.update(_ - 1)))(
                  _ => sem.release)
                .as(true)
            }

            sig
              .discrete
              .interruptWhen(tested.drain.covaryOutput[Boolean])
              .fold1(_.max(_))
              .compile
              .lastOrError
              .product(sig.get)
        }
        .flatTap(IO.println) mustEqual (n -> 0)
    }
  }

}
