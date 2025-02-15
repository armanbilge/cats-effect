/*
 * Copyright 2020-2024 Typelevel
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

import cats.effect.IO
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import scala.annotation.switch
import scala.concurrent.duration._

object TagBenchmark {
  val ioClassValue = new ClassValue[Byte] {
    private val PureClass = classOf[IO.Pure[?]]
    private val ErrorClass = classOf[IO.Error]
    private val DelayClass = classOf[IO.Delay[?]]
    private val ReadTimeClass = classOf[IO.RealTime.type]
    private val MonotonicClass = classOf[IO.Monotonic.type]
    private val ReadEcClass = classOf[IO.ReadEC.type]
    private val MapClass = classOf[IO.Map[?, ?]]
    private val FlatMapClass = classOf[IO.FlatMap[?, ?]]
    private val AttemptClass = classOf[IO.Attempt[?]]
    private val HandleErrorWithClass = classOf[IO.HandleErrorWith[?]]
    private val CanceledClass = classOf[IO.Canceled.type]
    private val OnCancelClass = classOf[IO.OnCancel[?]]
    private val UncancelableClass = classOf[IO.Uncancelable[?]]
    private val UnmaskRunLoopClass = classOf[IO.Uncancelable.UnmaskRunLoop[?]]
    private val IOContClass = classOf[IO.IOCont[?, ?]]
    private val GetClass = classOf[IO.IOCont.Get[?]]
    private val CedeClass = classOf[IO.Cede.type]
    private val StartClass = classOf[IO.Start[?]]
    private val RacePairClass = classOf[IO.RacePair[?, ?]]
    private val SleepClass = classOf[IO.Sleep]
    private val EvalOnClass = classOf[IO.EvalOn[?]]
    private val BlockingClass = classOf[IO.Blocking[?]]
    private val LocalClass = classOf[IO.Local[?]]
    private val IOTraceClass = classOf[IO.IOTrace.type]
    private val ReadRTClass = classOf[IO.ReadRT.type]
    private val EndFiberClass = classOf[IO.EndFiber.type]

    override def computeValue(clazz: Class[?]): Byte = (clazz: @unchecked) match {
      case PureClass => 0
      case ErrorClass => 1
      case DelayClass => 2
      case ReadTimeClass => 3
      case MonotonicClass => 4
      case ReadEcClass => 5
      case MapClass => 6
      case FlatMapClass => 7
      case AttemptClass => 8
      case HandleErrorWithClass => 9
      case CanceledClass => 10
      case OnCancelClass => 11
      case UncancelableClass => 12
      case UnmaskRunLoopClass => 13
      case IOContClass => 14
      case GetClass => 15
      case CedeClass => 16
      case StartClass => 17
      case RacePairClass => 18
      case SleepClass => 19
      case EvalOnClass => 20
      case BlockingClass => 21
      case LocalClass => 22
      case IOTraceClass => 23
      case ReadRTClass => 24
      case EndFiberClass => -1
    }

  }

  val values: Array[IO[?]] = Array(
    IO.Pure(1),
    IO.Error(new Throwable()),
    IO.delay(2),
    IO.RealTime,
    IO.Monotonic,
    IO.ReadEC,
    IO.Pure(3).map(_ + 1),
    IO.Pure(4).flatMap(_ => IO.pure(1)),
    IO.Attempt(IO.Pure(5)),
    IO.Pure(6).handleErrorWith(_ => IO.Pure(1)),
    IO.Canceled,
    IO.OnCancel(IO.Pure(7), IO(())),
    IO.uncancelable(_ => IO.Pure(8)),
    IO.Uncancelable.UnmaskRunLoop(IO.pure(9), -1, null),
    IO.IOCont(null, null),
    IO.IOCont.Get(null),
    IO.Cede,
    IO.Start(IO.pure(10)),
    IO.RacePair(IO.pure(11), IO.pure(12)),
    IO.Sleep(3.seconds),
    IO.EvalOn(IO.pure(13), null),
    IO.Blocking(null, null, null),
    IO.Local(null),
    IO.IOTrace,
    IO.ReadRT
  )

}

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TagBenchmark {

  @Benchmark
  def tag(bk: Blackhole): Unit = {
    var i = 0
    while (i < TagBenchmark.values.length) {
      val result: String = (TagBenchmark.values(i).tag: @switch) match {
        case 0 => "Pure"
        case 1 => "Error"
        case 2 => "Delay"
        case 3 => "ReadTime"
        case 4 => "Monotonic"
        case 5 => "ReadEC"
        case 6 => "Map"
        case 7 => "FlatMap"
        case 8 => "Attempt"
        case 9 => "HandleErrorWith"
        case 10 => "Canceled"
        case 11 => "OnCancel"
        case 12 => "Uncancelable"
        case 13 => "UnmaskRunLoop"
        case 14 => "IOCont"
        case 15 => "Get"
        case 16 => "Cede"
        case 17 => "Start"
        case 18 => "RacePair"
        case 19 => "Sleep"
        case 20 => "EvalOn"
        case 21 => "Blocking"
        case 22 => "Local"
        case 23 => "IOTrace"
        case 24 => "ReadRT"
        case -1 => "EndFiber"
      }
      bk.consume(result)
      i += 1
    }
  }

  @Benchmark
  def classValue(bk: Blackhole): Unit = {
    var i = 0
    while (i < TagBenchmark.values.length) {
      val result: String =
        (TagBenchmark.ioClassValue.get(TagBenchmark.values(i).getClass): @switch) match {
          case 0 => "Pure"
          case 1 => "Error"
          case 2 => "Delay"
          case 3 => "ReadTime"
          case 4 => "Monotonic"
          case 5 => "ReadEC"
          case 6 => "Map"
          case 7 => "FlatMap"
          case 8 => "Attempt"
          case 9 => "HandleErrorWith"
          case 10 => "Canceled"
          case 11 => "OnCancel"
          case 12 => "Uncancelable"
          case 13 => "UnmaskRunLoop"
          case 14 => "IOCont"
          case 15 => "Get"
          case 16 => "Cede"
          case 17 => "Start"
          case 18 => "RacePair"
          case 19 => "Sleep"
          case 20 => "EvalOn"
          case 21 => "Blocking"
          case 22 => "Local"
          case 23 => "IOTrace"
          case 24 => "ReadRT"
          case -1 => "EndFiber"
        }
      bk.consume(result)
      i += 1
    }
  }

  @Benchmark
  def classByteMap(bk: Blackhole): Unit = {
    val tags = IO.tags
    var i = 0
    while (i < TagBenchmark.values.length) {
      val result: String =
        (tags(TagBenchmark.values(i).getClass): @switch) match {
          case 0 => "Pure"
          case 1 => "Error"
          case 2 => "Delay"
          case 3 => "ReadTime"
          case 4 => "Monotonic"
          case 5 => "ReadEC"
          case 6 => "Map"
          case 7 => "FlatMap"
          case 8 => "Attempt"
          case 9 => "HandleErrorWith"
          case 10 => "Canceled"
          case 11 => "OnCancel"
          case 12 => "Uncancelable"
          case 13 => "UnmaskRunLoop"
          case 14 => "IOCont"
          case 15 => "Get"
          case 16 => "Cede"
          case 17 => "Start"
          case 18 => "RacePair"
          case 19 => "Sleep"
          case 20 => "EvalOn"
          case 21 => "Blocking"
          case 22 => "Local"
          case 23 => "IOTrace"
          case 24 => "ReadRT"
          case -1 => "EndFiber"
        }
      bk.consume(result)
      i += 1
    }
  }
}
