package catseffect

import cats.effect.{IO, IOApp}
//import cats.implicits._

object TestIOOps extends IOApp.Simple {
  def run: IO[Unit] = {
    for {
      _ <- Option(IO(1)).sequence
      _ <- Option(IO(1)).sequence_
      _ <- Option(IO(1)).parSequence
      _ <- Option(IO(1)).parSequence_
      _ <- List(IO(Option(1))).parSequenceFilter
      _ <- List(IO(1)).parUnorderedSequence
      _ <- List(IO(List(1))).flatSequence
      _ <- List(IO(List(1))).parFlatSequence
      _ <- (IO(1), IO(2)).parMapN(_ + _)
      _ <- (IO(1), IO(2)).parTupled
      _ <- (IO(1), IO(2)).parFlatMapN { case (x, y) => IO.pure(x + y) }
    } yield ()
  }
}
