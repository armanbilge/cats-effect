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
package kernel

import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.kernel.instances.all._
import cats.effect.kernel.testkit.PureConcGenerators._
import cats.effect.kernel.testkit.pure._
import cats.laws.discipline.AlignTests
import cats.laws.discipline.CommutativeApplicativeTests
import cats.laws.discipline.ParallelTests
import cats.laws.discipline.arbitrary.catsLawsCogenForIor
import org.typelevel.discipline.specs2.mutable.Discipline
import org.scalacheck.Prop, Prop.forAll

class ParallelFSpec extends BaseSpec with Discipline {

  // implicit val showIntInt = cats.Show.fromToString[Int => Int]

  // type F[A] = PureConc[Unit, A]
  // val F = GenConcurrent[F]
  // val fa: F[String] = F.pure("a") 
  // val fb: F[String] = F.pure("b")
  // val fc: F[Unit] = F.raiseError[Unit](())
  // run(ParallelF.value(ParallelF(fa).product(ParallelF(fb)).product(ParallelF(fc))).attempt.map(x => println(s"can we make it this far? $x")))
  // println(ParallelF.value(ParallelF(fa).product(ParallelF(fb)).product(ParallelF(fc))).show)
  // println(ParallelF.value(ParallelF(fa).product(ParallelF(fb))).show)
  // println(ParallelF.value(ParallelF(fc).product(ParallelF(fb).product(ParallelF(fa)))).show)


  // run(ParallelF.value(ParallelF(fbc).product(ParallelF(fab).product(ParallelF(fa)))))
  // run(ParallelF.value(ParallelF(fbc).product(ParallelF(fab).product(ParallelF(fa)))))
  // run(ParallelF.value(ParallelF(fab).ap(ParallelF(fa))))
  // run(ParallelF.value(ParallelF(fbc).ap(ParallelF(fab).ap(ParallelF(fa)))))
  // println(fa.show)
  // println(fab.show)
  // println(fbc.show)

  // "ParallelF" should {
  //   "not hang" in {
  //     val compose: (Int => Int) => (Int => Int) => (Int => Int) = _.compose
  //     // ParallelF(fbc).ap(ParallelF(fab).ap(ParallelF(fa))) eqv ParallelF(fbc)
  //     //   .map(compose)
  //     //   .ap(ParallelF(fab))
  //     //   .ap(ParallelF(fa))

  //     // This one hangs
  //     // ParallelF(fbc).ap(ParallelF(fab).ap(ParallelF(fa))) eqv ParallelF(F.pure(42))

  //     // This one does not
  //     // ParallelF(fbc).map(compose).ap(ParallelF(fab))ap(ParallelF(fa)) eqv ParallelF(F.pure(42))
  //   }
  // }

  // "ParallelF" should {
  //   "equal itself" in {
  //     forAll {
  //       (
  //           fa: PureConc[Int, Int],
  //           fab: PureConc[Int, Int => Int],
  //           fbc: PureConc[Int, Int => Int]) =>
  //         implicit val showIntInt = cats.Show.fromToString[Int => Int]
  //         val compose: (Int => Int) => (Int => Int) => (Int => Int) = _.compose
  //         println("START")
  //         println(fa.show)
  //         println(fab.show)
  //         println(fbc.show)
  //         println("STOP")
  //         val result = ParallelF(fbc).ap(ParallelF(fab).ap(ParallelF(fa))) eqv ParallelF(fbc).map(compose).ap(ParallelF(fab)).ap(ParallelF(fa))
  //         if (!result) {
  //           println(fa.show)
  //           println(fab.show)
  //           println(fbc.show)
  //         }
  //         result
  //     }
  //   }
  // }

  checkAll(
    "Parallel[F, ParallelF]",
    ParallelTests[PureConc[Int, *], ParallelF[PureConc[Int, *], *]].parallel[Int, Int])

  checkAll(
    "CommutativeApplicative[ParallelF]",
    CommutativeApplicativeTests[Option].applicative[Int, Int, Int])

  checkAll(
    "Align[ParallelF]",
    AlignTests[ParallelF[PureConc[Int, *], *]].align[Int, Int, Int, Int])

}
