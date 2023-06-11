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

package cats.effect.benchmarks

import cats.effect._
import cats.syntax.all._

import scala.annotation.nowarn

@nowarn
object BenchmarkMain extends IOApp {
  def run(args: List[String]) = args match {
    case bench :: replicates :: Nil =>
      val n = replicates.toInt

      val benchIO = bench match {
        case "DeepBind.pure" => DeepBindBenchmark.pure
        case "DeepBind.delay" => DeepBindBenchmark.delay
        case "DeepBind.async" => DeepBindBenchmark.async
        case "MapCalls.one" => MapCallsBenchmark.one
        case "MapCalls.batch30" => MapCallsBenchmark.batch30
        case "MapCalls.batch120" => MapCallsBenchmark.batch120
      }

      benchIO.void.combineN(n).as(ExitCode.Success)
  }

}
