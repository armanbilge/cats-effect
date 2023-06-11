package cats.effect.benchmarks

import cats.effect.IO
import cats.syntax.all._

object MapCallsBenchmark {
  def one: IO[Int] = test(12000, 1)

  def batch30: IO[Int] = test(12000 / 30, 30)

  def batch120: IO[Int] = test(12000 / 120, 120)

  private[this] def test(iterations: Int, batch: Int): IO[Int] = {
    val f = (x: Int) => x + 1
    var io = IO(0)

    var j = 0
    while (j < batch) { io = io.map(f); j += 1 }

    io.combineN(iterations)
  }
}
