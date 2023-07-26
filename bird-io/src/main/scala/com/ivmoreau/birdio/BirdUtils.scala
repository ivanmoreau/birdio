/*
 * Copyright (c) Iván Molina Rebolledo 2023.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.birdio

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import com.twitter.util.{Duration, Future, FuturePool, JavaTimer, Monitor, Promise, ScheduledThreadPoolTimer}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import cats.effect.IO

import java.time.Instant
import java.time.temporal.ChronoField

object BirdUtils {

  /** Generate a BirdIO ExecutionContext from the Local#Context of the current Future thread. This is a non-blocking EC.
    */
  def genExecutionContext: BirdIO[ExecutionContext] =
    BirdIO {
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = Future(runnable.run())
        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }
    }

  /** Generate a BirdIO ExecutionContext from the Local#Context of the current Future thread. This is a blocking EC.
    */
  def genExecutionContextBlocking: BirdIO[ExecutionContext] =
    BirdIO {
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = FuturePool.unboundedPool(runnable.run())
        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }
    }

  /** Runs a Cats IO task in a Twitter Future context. */
  def runIO[A](io: IO[A]): Future[A] = {
    val computeEC: ExecutionContext =
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = Future(runnable.run())

        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }

    val blockingEC: ExecutionContext =
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = FuturePool.unboundedPool(runnable.run())

        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }

    val scheduler: Scheduler = new Scheduler {
      override def sleep(delay: FiniteDuration, task: Runnable): Runnable = new Runnable {
        override def run(): Unit = Future.Unit.flatMap { _ =>
          implicit val timer: JavaTimer = new JavaTimer()
          Future.sleep(Duration.fromMilliseconds(delay.toMillis))
        }
      }

      override def nowMillis(): Long = System.currentTimeMillis()

      override def monotonicNanos(): Long = {
        val now = Instant.now()
        now.getEpochSecond * 1000000 + now.getLong(ChronoField.MICRO_OF_SECOND)
      }
    }

    val config: IORuntimeConfig = IORuntimeConfig()
    val runtime: IORuntime =
      IORuntime.apply(
        compute = computeEC,
        blocking = blockingEC,
        scheduler = scheduler,
        shutdown = () => (),
        config = config
      )

    val promise: Promise[A] = Promise[A]()

    io.unsafeRunAsync {
      case Left(e)  => promise.setException(e)
      case Right(a) => promise.setValue(a)
    }(runtime)

    promise
  }
}

final class BirdUtilsOps[A](val io: IO[A]) extends AnyVal {

  /** Warning: Deprecated. Use [[runInXFuture]] instead. */
  def runInTwitter(): Future[A] = BirdUtils.runIO(io)
  def runInXFuture(): Future[A] = BirdUtils.runIO(io)
}
