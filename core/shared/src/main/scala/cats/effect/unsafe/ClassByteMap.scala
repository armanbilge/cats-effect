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

package cats.effect.unsafe

import java.util.Arrays

import ClassByteMap.{Size, Mask}

private[effect] final class ClassByteMap {

  private[this] val keys = new Array[Class[_]](Size)
  private[this] val values = {
    val a = new Array[Byte](Size)
    Arrays.fill(a, -1: Byte)
    a
  }

  def apply(key: Class[_]): Byte = {
    var i = key.hashCode() & Mask
    while (keys(i) != key) i = (i + 1) & Mask
    values(i)
  }

  def update(key: Class[_], value: Byte): Unit = {
    var i = key.hashCode() & Mask
    while (values(i) != -1) i = (i + 1) & Mask
    keys(i) = key
    values(i) = value
  }

}

private[effect] object ClassByteMap {

  private final val Size = 64
  private final val Mask = Size - 1

}
