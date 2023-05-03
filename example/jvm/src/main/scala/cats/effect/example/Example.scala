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

package example

import cats.effect._
import cats.effect.std._
import cats.syntax.all._

object Example extends IOApp.Simple {

  override def computeWorkerThreadCount = 2

  def run: IO[Unit] = Dispatcher
    .parallel[IO](await = true)
    .allocated
    .flatMap {
      case (runner, release) =>
        IO(runner.unsafeRunAndForget(release))
    }
    .replicateA_(20000)
}
