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

import cats.effect._
import fs2._

import scala.concurrent.duration._

object App extends IOApp.Simple {
  override def computeWorkerThreadCount = 2

  override def runtimeConfig = 
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)

  def run = {
    val interruptSoon = (IO(println("huh")) *> IO.sleep(20.millis) *> IO {
      println("ho"); System.out.flush()
    }).attempt.debug()
    Stream.constant(true).interruptWhen(interruptSoon).compile.drain.debug().replicateA(2).void
  }
}
