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

package com.ivmoreau.birdio

import cats.effect.testkit.TestInstances
import cats.effect.{Async, IO}
import cats.effect.laws.{AsyncLaws, AsyncTests}
import cats.laws.discipline.arbitrary.*
import munit.DisciplineSuite
import org.scalacheck.Arbitrary.arbitrary
import cats.laws.discipline.arbitrary.*
import org.scalacheck.{Arbitrary, Cogen, Prop}

import scala.concurrent.duration.DurationInt
import BirdUtils.*
import cats.{Eq, Order}
import cats.effect.kernel.{Outcome, Sync}
import com.twitter.util.Await

import scala.concurrent.ExecutionContext

object BirdIOAsyncLaws extends AsyncTests[BirdIO] {
  override val laws: AsyncLaws[BirdIO] = new AsyncLaws[BirdIO] {
    override implicit val F: Async[BirdIO] = BirdIO.asyncForBirdIO
  }
}

object t extends TestInstances

class BirdIOAsyncLawsTest extends DisciplineSuite {

  import t.*
  implicit val ticker = Ticker()

  implicit def cogenOutcomeAsyncIO[A](implicit
      cogenOutcomeIO: Cogen[Outcome[IO, Throwable, A]]
  ): Cogen[Outcome[BirdIO, Throwable, A]] =
    cogenOutcomeIO.contramap {
      case Outcome.Canceled()     => Outcome.Canceled()
      case Outcome.Errored(e)     => Outcome.Errored(e)
      case Outcome.Succeeded(aio) => Outcome.Succeeded(BirdUtils.birdIOToIO(aio))
    }

  implicit def eqAsyncIO[A](implicit eqIO: Eq[IO[A]]): Eq[BirdIO[A]] = (x, y) =>
    eqIO.eqv(BirdUtils.birdIOToIO(x), BirdUtils.birdIOToIO(y))

  implicit def arbitraryAsyncIO[A](implicit arbIO: Arbitrary[IO[A]]): Arbitrary[BirdIO[A]] =
    Arbitrary(arbitrary[IO[A]].map(a => BirdIO.unsafeFromTwitterFuture(a.runInTwitter())))

  implicit def orderAsyncIO[A](implicit orderIO: Order[IO[A]]): Order[BirdIO[A]] = (x, y) =>
    orderIO.compare(BirdUtils.birdIOToIO(x), BirdUtils.birdIOToIO(y))

  implicit def execAsyncIO[A](implicit execIO: IO[A] => Prop): BirdIO[A] => Prop = aio =>
    execIO(BirdUtils.birdIOToIO(aio))

  checkAll("BirdIO", BirdIOAsyncLaws.async[Int, Int, Int](10.millis))
}
