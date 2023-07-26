package com.ivmoreau.birdio

import cats.data.State
import cats.effect.kernel.Sync.Type
import cats.{MonadError, Show}
import cats.effect.kernel.{Async, Cont, Deferred, Fiber, Outcome, Poll, Ref, Sync, Temporal, Unique}
import cats.effect.std.Console
import com.twitter.util.{Future, FutureCancelledException, FuturePool, Promise, Return, Throw, Try}

import java.nio.charset.Charset
import java.time.Instant
import java.time.temporal.ChronoField
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

/** A BirdIO[A] is a encapsulation of a Future[A]. It tries to stay as close as possible to the cats.effect.IO data
  * type.
  */
sealed abstract class BirdIO[+A] private (private val future: () => Future[A]) {

  def flatMap[B](value: A => BirdIO[B]): BirdIO[B] =
    new BirdIO[B](() => future().flatMap(a => value(a).future())) {}

  def flatten[B](implicit ev: A <:< BirdIO[B]): BirdIO[B] = flatMap(ev)

  def map[B](f: A => B): BirdIO[B] =
    new BirdIO[B](() => future().map(f)) {}

  def *>[B](fb: BirdIO[B]): BirdIO[B] = flatMap(_ => fb)
  def <*[B](fb: BirdIO[B]): BirdIO[A] = flatMap(a => fb.map(_ => a))
  def >>[B](fb: => BirdIO[B]): BirdIO[B] = flatMap(_ => fb)
  def <<[B](fb: => BirdIO[B]): BirdIO[A] = flatMap(a => fb.map(_ => a))

  def handleErrorWith[AA >: A](f: Throwable => BirdIO[AA]): BirdIO[AA] =
    new BirdIO[AA](() => future().rescue { case e => f(e).future() }) {}

  def handleError[AA >: A](f: Throwable => AA): BirdIO[AA] =
    new BirdIO[AA](() => future().handle { case e => f(e) }) {}

  def void: BirdIO[Unit] = map(_ => ())

  def run(): Future[A] = future()
}

object BirdIO {
  def apply[A](thunk: => A): BirdIO[A] = delay(thunk)

  def pure[A](a: A): BirdIO[A] =
    new BirdIO[A](() => Future.value(a)) {}

  def blocking[A](action: => A): BirdIO[A] =
    new BirdIO[A](() => {
      FuturePool.interruptibleUnboundedPool {
        action
      }
    }) {}

  /** Twitter Futures are now called X Futures. */
  def unsafeFromXFuture[A](future: => Future[A]): BirdIO[A] =
    new BirdIO[A](() => future) {}

  /** Warning: Deprecated. Use [[unsafeFromXFuture]] instead, as Twitter is now called X. */
  def unsafeFromTwitterFuture[A](future: => Future[A]): BirdIO[A] =
    new BirdIO[A](() => future) {}

  def raiseError[A](err: Throwable): BirdIO[A] = new BirdIO[A](() => Future.exception(err)) {}

  def raiseUnless[A](cond: Boolean)(err: => Throwable): BirdIO[Unit] =
    if (cond) pure(()) else raiseError(err)

  def raiseWhen[A](cond: Boolean)(err: => Throwable): BirdIO[Unit] =
    if (cond) raiseError(err) else pure(())

  def unlessA[A](cond: Boolean)(action: => BirdIO[Unit]): BirdIO[Unit] =
    if (cond) pure(()) else action

  def whenA[A](cond: Boolean)(action: => BirdIO[Unit]): BirdIO[Unit] =
    if (cond) action else pure(())

  def none[A]: BirdIO[Option[A]] = pure(None)

  def some[A](a: A): BirdIO[Option[A]] = pure(Some(a))

  def unit: BirdIO[Unit] = pure(())

  def never[A]: BirdIO[A] = new BirdIO[A](() => Future.never) {}

  def delay[A](thunk: => A): BirdIO[A] = {
    new BirdIO[A](() => Future.apply(thunk)) {}
  }

  def defer[A](thunk: => BirdIO[A]): BirdIO[A] =
    delay(thunk).flatten

  /** This is a bogus implementation. It does not support cancellation.
    */
  def async[A](k: (Either[Throwable, A] => Unit) => BirdIO[Option[BirdIO[Unit]]]): BirdIO[A] = {
    val promise = Promise[A]()

    // If this throws, is because the promise is already completed.
    Try {
      k.apply {
        case Left(err) => promise.setException(err)
        case Right(a)  => promise.setValue(a)
      }
    }.map(_.void).getOrElse(unit).flatMap(_ => new BirdIO[A](() => promise) {})
  }

  def async_[A](k: (Either[Throwable, A] => Unit) => Unit): BirdIO[A] = {
    val promise = Promise[A]()

    // If this throws, is because the promise is already completed.
    Try {
      k.apply {
        case Left(err) => promise.setException(err)
        case Right(a)  => promise.setValue(a)
      }
    }
    new BirdIO[A](() => promise) {}
  }

  def canceled: BirdIO[Unit] = raiseError(new FutureCancelledException)

  // The implementation is a bogus one.
  def cede: BirdIO[Unit] = new BirdIO[Unit](() => FuturePool.unboundedPool(Future.Unit)) {}

  def executionContext: BirdIO[ExecutionContext] =
    raiseError(new NotImplementedError("BirdIO does not support ExecutionContext"))

  def executor: BirdIO[Executor] =
    raiseError(new NotImplementedError("BirdIO does not support Executor"))

  def monotonic: BirdIO[FiniteDuration] = {
    def t(): Long = {
      val now = Instant.now()
      now.getEpochSecond * 1000000 + now.getLong(ChronoField.MICRO_OF_SECOND)
    }
    import java.lang.System.nanoTime
    assert(t() - t() == 0, "System.nanoTime is not monotonic")
    new BirdIO[FiniteDuration](() => Future.value(Duration.fromNanos(t()))) {}
  }

  def realTime: BirdIO[FiniteDuration] = {
    import java.lang.System.currentTimeMillis
    new BirdIO[FiniteDuration](() => Future.value(Duration.fromNanos(currentTimeMillis() * 1000000))) {}
  }

  def sleep(delay: Duration): BirdIO[Unit] = {
    import com.twitter.util.Duration as TwitterDuration
    implicit val timer: com.twitter.util.Timer = new com.twitter.util.JavaTimer(true)
    new BirdIO[Unit](() => Future.sleep(TwitterDuration.fromNanoseconds(delay.toNanos))) {}
  }

  def sleep(finiteDelay: FiniteDuration): BirdIO[Unit] =
    sleep(finiteDelay: Duration)

  // Bogus implementation
  def uncancelable[A](body: Poll[BirdIO] => BirdIO[A]): BirdIO[A] =
    body(new Poll[BirdIO] {
      override def apply[B](fa: BirdIO[B]): BirdIO[B] = fa
    })

  // utilities

  def stub: BirdIO[Nothing] = {
    val e = new NotImplementedError("BirdIO goes whale 🐳")
    raiseError(e)
  }

  def ref[A](a: A): BirdIO[Ref[BirdIO, A]] = BirdIO(new Ref[BirdIO, A] {
    private val birdVar: AtomicReference[A] = new AtomicReference[A](a)

    override def access: BirdIO[(A, A => BirdIO[Boolean])] = BirdIO {
      val oldState = birdVar.get()
      (oldState, (newState: A) => BirdIO(birdVar.compareAndSet(oldState, newState)))
    }

    override def tryUpdate(f: A => A): BirdIO[Boolean] = BirdIO {
      val oldState = birdVar.get()
      val newState = f(oldState)
      birdVar.compareAndSet(oldState, newState)
    }

    override def tryModify[B](f: A => (A, B)): BirdIO[Option[B]] = BirdIO {
      val oldState = birdVar.get()
      val (newState, result) = f(oldState)
      if (birdVar.compareAndSet(oldState, newState)) {
        Some(result)
      } else {
        None
      }
    }

    override def update(f: A => A): BirdIO[Unit] = {
      @tailrec
      def compute(current: A): BirdIO[Unit] = {
        val next = f(current)
        if (birdVar.compareAndSet(current, next)) {
          unit
        } else {
          compute(next)
        }
      }
      compute(a)
    }

    override def modify[B](f: A => (A, B)): BirdIO[B] =
      tryModify(f).flatMap {
        case Some(value) => pure(value)
        case None        => modify(f)
      }

    override def tryModifyState[B](state: State[A, B]): BirdIO[Option[B]] = BirdIO {
      val oldState = birdVar.get()
      val (newState, result) = state.run(oldState).value
      if (birdVar.compareAndSet(oldState, newState)) {
        Some(result)
      } else {
        None
      }
    }

    override def modifyState[B](state: State[A, B]): BirdIO[B] =
      tryModifyState(state).flatMap {
        case Some(value) => pure(value)
        case None        => modifyState(state)
      }

    override def get: BirdIO[A] = BirdIO(birdVar.get())

    override def set(a: A): BirdIO[Unit] = BirdIO(birdVar.set(a))
  })

  def deferred[A]: BirdIO[Deferred[BirdIO, A]] = BirdIO(new Deferred[BirdIO, A] {
    private val hiddenPromise: Promise[A] = Promise[A]()

    override def complete(a: A): BirdIO[Boolean] = if (hiddenPromise.isDefined) {
      pure(false)
    } else {
      pure(hiddenPromise.setValue(a)) *> pure(true)
    }

    override def get: BirdIO[A] = new BirdIO(() => hiddenPromise) {}

    override def tryGet: BirdIO[Option[A]] = BirdIO(hiddenPromise.poll.flatMap {
      case Return(r) => Some(r)
      case Throw(_)  => None
    })
  })

  def unique: BirdIO[Unique.Token] = BirdIO(new Unique.Token)

  def fromOption[A](o: Option[A])(orElse: => Throwable): BirdIO[A] =
    o match {
      case None        => raiseError(orElse)
      case Some(value) => pure(value)
    }

  def fromEither[A](e: Either[Throwable, A]): BirdIO[A] =
    e match {
      case Right(a)  => pure(a)
      case Left(err) => raiseError(err)
    }

  def fromTry[A](t: Try[A]): BirdIO[A] =
    t match {
      case Return(a) => pure(a)
      case Throw(e)  => raiseError(e)
    }

  // instances

  trait MonadErrorForBirdIO extends MonadError[BirdIO, Throwable] {
    override def raiseError[A](e: Throwable): BirdIO[A] = BirdIO.raiseError(e)

    override def handleErrorWith[A](fa: BirdIO[A])(f: Throwable => BirdIO[A]): BirdIO[A] =
      fa.handleErrorWith(f)

    override def pure[A](x: A): BirdIO[A] = BirdIO.pure(x)

    override def flatMap[A, B](fa: BirdIO[A])(f: A => BirdIO[B]): BirdIO[B] =
      fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => BirdIO[Either[A, B]]): BirdIO[B] =
      f(a).flatMap {
        case Left(value)  => tailRecM(value)(f)
        case Right(value) => pure(value)
      }
  }

  implicit val monadErrorForBirdIO: MonadError[BirdIO, Throwable] = new MonadErrorForBirdIO {}

  class AsyncForBirdIO extends Async[BirdIO] with MonadErrorForBirdIO {

    /** Bogus implementation of evalOn as BirdIO does not use ExecutionContext
      */
    override def evalOn[A](fa: BirdIO[A], ec: ExecutionContext): BirdIO[A] =
      fa

    /** Bogus implementation of executionContext as BirdIO does not use ExecutionContext
      */
    override def executionContext: BirdIO[ExecutionContext] = BirdIO.executionContext

    override def async[A](k: (Either[Throwable, A] => Unit) => BirdIO[Option[BirdIO[Unit]]]): BirdIO[A] =
      BirdIO.async(k)

    override def async_[A](k: (Either[Throwable, A] => Unit) => Unit): BirdIO[A] = BirdIO.async_(k)

    override def monotonic: BirdIO[FiniteDuration] =
      BirdIO.monotonic

    override def realTime: BirdIO[FiniteDuration] =
      BirdIO.realTime

    override def ref[A](a: A): BirdIO[Ref[BirdIO, A]] =
      BirdIO.ref(a)

    override def deferred[A]: BirdIO[Deferred[BirdIO, A]] =
      BirdIO.deferred

    override def suspend[A](hint: Sync.Type)(thunk: => A): BirdIO[A] = hint match {
      case Type.Blocking => BirdIO.blocking(thunk)
      case Type.Delay    => BirdIO(thunk)
      case _             => BirdIO(thunk)
    }

    override protected def sleep(time: FiniteDuration): BirdIO[Unit] =
      BirdIO.sleep(time)

    override def start[A](fa: BirdIO[A]): BirdIO[Fiber[BirdIO, Throwable, A]] =
      BirdIO(new Fiber[BirdIO, Throwable, A] {
        val current: Future[A] = fa.run()

        override def cancel: BirdIO[Unit] = BirdIO(current.raise(new FutureCancelledException))

        override def join: BirdIO[Outcome[BirdIO, Throwable, A]] = BirdIO {
          current.poll match {
            case Some(Return(a))                          => Outcome.Succeeded(BirdIO.pure(a))
            case Some(Throw(e: FutureCancelledException)) => Outcome.Canceled()
            case Some(Throw(e))                           => Outcome.Errored(e)
            case None                                     => Outcome.Canceled()
          }
        }
      })

    override def cede: BirdIO[Unit] =
      BirdIO.cede

    override def forceR[A, B](fa: BirdIO[A])(fb: BirdIO[B]): BirdIO[B] =
      fa.void.handleErrorWith(_ => unit) *> fb

    override def uncancelable[A](body: Poll[BirdIO] => BirdIO[A]): BirdIO[A] =
      BirdIO.uncancelable(body)

    override def canceled: BirdIO[Unit] =
      BirdIO.canceled

    override def onCancel[A](fa: BirdIO[A], fin: BirdIO[Unit]): BirdIO[A] =
      fa.handleErrorWith {
        case err: FutureCancelledException => fin *> raiseError(err)
        case err                           => raiseError(err)
      }

    override def cont[K, R](body: Cont[BirdIO, K, R]): BirdIO[R] =
      Async.defaultCont(body)(this)
  }

  implicit val asyncForBirdIO: Async[BirdIO] = new AsyncForBirdIO {}

  implicit val temporalForIO: Temporal[BirdIO] = new AsyncForBirdIO {}

  implicit val consoleForIO: Console[BirdIO] = new Console[BirdIO] {

    /** Charset is ignored, bogus impl */
    override def readLineWithCharset(charset: Charset): BirdIO[String] = {
      blocking(scala.io.StdIn.readLine())
    }

    override def print[A](a: A)(implicit S: Show[A]): BirdIO[Unit] = {
      blocking(print(S.show(a)))
    }

    override def println[A](a: A)(implicit S: Show[A]): BirdIO[Unit] = {
      blocking(println(S.show(a)))
    }

    override def error[A](a: A)(implicit S: Show[A]): BirdIO[Unit] = {
      blocking(System.err.print(S.show(a))) *> raiseError(new Exception(S.show(a)))
    }

    override def errorln[A](a: A)(implicit S: Show[A]): BirdIO[Unit] =
      blocking(System.err.println(S.show(a))) *> raiseError(new Exception(S.show(a)))
  }
}
