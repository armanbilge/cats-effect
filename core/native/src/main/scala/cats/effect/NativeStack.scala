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

package cats.effect

import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.runtime.RawPtr

private object NativeStack {
  @inline
  private[this] def toPtr(stack: Array[Object], pos: Int): RawPtr =
    elemRawPtr(castObjectToRawPtr(stack), (8 * pos + 16).toLong)

  @inline
  private[this] def size(stack: Array[Object]): Int =
    loadInt(toPtr(stack, 0))

  @inline
  private[this] def setSize(stack: Array[Object], size: Int): Unit =
    storeInt(toPtr(stack, 0), size)

  def create(size: Int): Array[Object] =
    new Array[Object](1 + size)

  def pushObject(stack: Array[Object], obj: Object): Array[Object] = {
    val i = size(stack)
    val use = growIfNeeded(stack, i)
    use(i + 1) = obj
    setSize(use, i + 1)
    use
  }

  def pushCont(stack: Array[Object], cont: Int): Array[Object] = {
    val i = size(stack)
    val use = growIfNeeded(stack, i)
    storeInt(toPtr(use, i + 1), cont)
    setSize(use, i + 1)
    use
  }

  def pushObjectCont(stack: Array[Object], obj: Object, cont: Int): Array[Object] = {
    val i = size(stack)
    val use = growIfNeeded(stack, i + 1)
    use(i + 1) = obj
    storeInt(toPtr(use, i + 2), cont)
    setSize(use, i + 2)
    use
  }

  def popCont(stack: Array[Object]): Int = {
    val i = size(stack)
    val ptr = toPtr(stack, i)
    val cont = loadInt(ptr)
    storeInt(ptr, 0)
    setSize(stack, i - 1)
    cont
  }

  def popObject(stack: Array[Object]): Object = {
    val i = size(stack)
    val obj = stack(i)
    stack(i) = null
    setSize(stack, i - 1)
    obj
  }

  private[this] def growIfNeeded(stack: Array[Object], index: Int): Array[Object] = {
    if (1 + index < stack.length) {
      stack
    } else {
      val bigger = new Array[Object](stack.length << 1)
      System.arraycopy(stack, 0, bigger, 0, stack.length)
      bigger
    }
  }

}

// private object NativeStack {
//   @inline
//   private[this] def size(stack: Array[Object]): Int =
//     stack(0).asInstanceOf[Int]

//   @inline
//   private[this] def setSize(stack: Array[Object], size: Int): Unit =
//     stack(0) = Integer.valueOf(size)

//   def create(size: Int): Array[Object] =
//     new Array[Object](1 + size)

//   def pushObject(stack: Array[Object], obj: Object): Array[Object] = {
//     val i = size(stack)
//     val use = growIfNeeded(stack, i)
//     use(i + 1) = obj
//     setSize(use, i + 1)
//     use
//   }

//   def pushCont(stack: Array[Object], cont: Int): Array[Object] = {
//     val i = size(stack)
//     val use = growIfNeeded(stack, i)
//     use(i + 1) = Integer.valueOf(cont)
//     setSize(use, i + 1)
//     use
//   }

//   def pushObjectCont(stack: Array[Object], obj: Object, cont: Int): Array[Object] = {
//     val i = size(stack)
//     val use = growIfNeeded(stack, i + 1)
//     use(i + 1) = obj
//     use(i + 2) = Integer.valueOf(cont)
//     setSize(use, i + 2)
//     use
//   }

//   def popCont(stack: Array[Object]): Int = {
//     val i = size(stack)
//     val cont = stack(i).asInstanceOf[Int]
//     setSize(stack, i - 1)
//     cont
//   }

//   def popObject(stack: Array[Object]): Object = {
//     val i = size(stack)
//     val obj = stack(i)
//     stack(i) = null
//     setSize(stack, i - 1)
//     obj
//   }

//   private[this] def growIfNeeded(stack: Array[Object], index: Int): Array[Object] = {
//     if (1 + index < stack.length) {
//       stack
//     } else {
//       val bigger = new Array[Object](stack.length << 1)
//       System.arraycopy(stack, 0, bigger, 0, stack.length)
//       bigger
//     }
//   }

// }
