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

package tracing

import cats.effect.BaseSpec
import cats.effect.IO

class TracingSpec extends BaseSpec {

  "IO.delay" should {
    "have different traces" in real {

      def f = IO.unit.flatMap { _ =>
        g
      }
      def g = IO { throw new Exception("g") }
      def h = IO { throw new Exception("h") }

      f.attempt.product(h.attempt).flatMap {
        case (Left(fex), Left(hex)) =>
          IO {
            fex.printStackTrace()
            hex.printStackTrace()
            !hex.getStackTrace().exists(_.getClassName().startsWith("flatMap @"))
          }
        case _ => IO.pure(false)
      }

    }
  }

}
