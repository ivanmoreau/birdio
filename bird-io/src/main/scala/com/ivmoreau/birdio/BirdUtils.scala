/*
 * Copyright (c) Iván Molina Rebolledo 2023.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.birdio

import cats.effect.unsafe.{IORuntime, Scheduler}
import com.twitter.util.{Future, FuturePool, Monitor, ScheduledThreadPoolTimer}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object BirdUtils {

  /** Generate a BirdIO ExecutionContext from the Local#Context of the current Future thread. This is a non-blocking
    * EC.
    */
  def genExecutionContext: BirdIO[ExecutionContext] =
    BirdIO {
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = Future(runnable.run())
        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }
    }

  /** Generate a BirdIO ExecutionContext from the Local#Context of the current Future thread. This is a blocking
    * EC.
    */
  def genExecutionContextBlocking: BirdIO[ExecutionContext] =
    BirdIO {
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = FuturePool.unboundedPool(runnable.run())
        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }
    }

  /** Runs a Cats IO task in a Twitter Future context. */
  def runIO[A](io: BirdIO[A]): Future[A] = {
    val computeEC: BirdIO[ExecutionContext] = genExecutionContext
    val blockingEC: BirdIO[ExecutionContext] = genExecutionContextBlocking
    val scheduler: Scheduler = new Scheduler {
      override def sleep(delay: FiniteDuration, task: Runnable): Runnable =

      override def nowMillis(): Long = ???

      override def monotonicNanos(): Long = ???
    }
    val runtime: IORuntime = IORuntime.apply()
  }
}
