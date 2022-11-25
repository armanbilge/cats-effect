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

package cats.effect.kernel;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

// defined in Java since Scala doesn't let us define static fields
@SuppressWarnings({"rawtypes", "serial", "unchecked"})
class SyncRef extends Ref {

  private volatile Object value;
  private final Sync F;

  SyncRef(Object F, Object value) {
    this.F = (Sync) F;
    this.value = value;
  }

  public Object get() {
    return F.delay(() -> updater.get(this));
  }

  public Object set(Object a) {
    return F.delay(
        () -> {
          updater.set(this, a);
          return scala.runtime.BoxedUnit.UNIT;
        });
  }

  public Object getAndSet(Object a) {
    return F.delay(() -> updater.getAndSet(this, a));
  }

  public Object modify(scala.Function1 f) {
    return F.delay(() -> {
      Object b, c, u;
      do {
        c = updater.get(this);
        scala.Tuple2 ub = (scala.Tuple2) f.apply(c);
        u = ub._1();
        b = ub._2();
      } while (!updater.compareAndSet(this, c, u));
      return b;
    });
  }

  public Object tryModify(scala.Function1 f) {
    return null;
  }

  public Object update(scala.Function1 f) {
    return null;
  }

  public Object tryUpdate(scala.Function1 f) {
    return null;
  }

  public Object access() {
    return null;
  }

  public Object modifyState(cats.data.IndexedStateT state) {
    return null;
  }

  public Object tryModifyState(cats.data.IndexedStateT state) {
    return null;
  }

  static final AtomicReferenceFieldUpdater<SyncRef, Object> updater =
      AtomicReferenceFieldUpdater.newUpdater(SyncRef.class, Object.class, "value");
}
