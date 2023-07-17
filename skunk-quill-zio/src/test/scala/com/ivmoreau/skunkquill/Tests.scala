package com.ivmoreau.skunkquill

import cats.Show
import cats.effect.Resource
import cats.effect.std.Console
import cats.implicits.catsSyntaxApplicativeError
import io.getquill.SnakeCase
import skunk.Session
import natchez.Trace.Implicits.noop
import zio.{Task, ZIO}
import zio.test.{TestAspect, ZIOSpecDefault, assertTrue}
import zio.interop.catz.*

import java.nio.charset.Charset

// Test table
case class Postzio(username: String, text: String)

object SkunkQuillZIOTest extends ZIOSpecDefault {

  implicit val consoleZIO: Console[Task] = new Console[Task] {
    override def readLineWithCharset(charset: Charset): Task[String] =
      ZIO.attempt(scala.io.StdIn.readLine())

    override def print[A](a: A)(implicit S: Show[A]): Task[Unit] =
      ZIO.attempt(print(a))

    override def println[A](a: A)(implicit S: Show[A]): Task[Unit] =
      ZIO.attempt(println(a))

    override def error[A](a: A)(implicit S: Show[A]): Task[Unit] =
      ZIO.attempt(System.err.print(a)) *> ZIO.fail(new Exception("error"))

    override def errorln[A](a: A)(implicit S: Show[A]): Task[Unit] =
      ZIO.attempt(System.err.println(a)) *> ZIO.fail(new Exception("error"))
  }

  val db: Resource[Task, SkunkContextZIO[SnakeCase]] = {
    val session: Resource[Task, Session[Task]] =
      Session.single(
        host = "localhost",
        port = 5432,
        user = "test",
        database = "testpgdb",
        password = Some("test")
      )
    val newCtx = session
      .map(s => new SkunkContextZIO[SnakeCase](SnakeCase, s))
    newCtx.flatMap(ctx => {
      val zio: Task[SkunkContextZIO[SnakeCase]] = ZIO.succeed(ctx)
      Resource.make(zio)(ctx => {
        import ctx.*
        ctx.run(query[Postzio].delete).unit // This also serves as a test that delete works LOL
      })
    })
  }

  def spec = suite("SkunkQuillZIOTest")(
    test("insertion and selection works") {
      db.use { ctx =>
        import ctx.*
        for {
          _ <- ctx.run(query[Postzio].insertValue(Postzio("Joe", "Hello")))
          _ <- ctx.run(query[Postzio].insertValue(Postzio("Jack", "Hello")))
          result <- ctx.run(query[Postzio].filter(_.username == "Joe"))
        } yield assertTrue(result == List(Postzio("Joe", "Hello")))
      }
    },
    test("insertion and selection works 2") {
      db.use { ctx =>
        import ctx.*
        for {
          _ <- ctx.run(query[Postzio].insertValue(Postzio("Joe", "Hello")))
          _ <- ctx.run(query[Postzio].insertValue(Postzio("Jack", "Hello")))
          result <- ctx.run(query[Postzio].filter(_.username == "Jack"))
        } yield assertTrue(result == List(Postzio("Jack", "Hello")))
      }
    },
    test("transaction works") {
      db.use { ctx =>
        import ctx.*
        for {
          _ <- ctx.run(query[Postzio].insertValue(Postzio("Joe", "Hello")))
          _ <- transaction {
            for {
              _ <- ctx.run(query[Postzio].insertValue(Postzio("Joe", "Hello")))
              _ <- ctx.run(query[Postzio].insertValue(Postzio("Jack", "Hello")))
              _ <- ZIO.fail(new Exception("rollback"))
            } yield ()
          }.handleError(_ => ())
          resultsJoe <- ctx.run(query[Postzio].filter(_.username == "Joe"))
          resultsJack <- ctx.run(query[Postzio].filter(_.username == "Jack"))
        } yield {
          assertTrue(resultsJoe == List(Postzio("Joe", "Hello")))
          assertTrue(resultsJack.isEmpty)
        }
      }
    },
    test("transactionTry works") {
      db.use { ctx =>
        import ctx.*
        for {
          _ <- ctx.run(query[Postzio].insertValue(Postzio("Joe", "Hello")))
          _ <- transactionTry {
            for {
              _ <- ctx.run(query[Postzio].insertValue(Postzio("Joe", "Hello")))
              _ <- ctx.run(query[Postzio].insertValue(Postzio("Jack", "Hello")))
              _ <- ZIO.fail(new Exception("rollback"))
            } yield ()
          }
          resultsJoe <- ctx.run(query[Postzio].filter(_.username == "Joe"))
          resultsJack <- ctx.run(query[Postzio].filter(_.username == "Jack"))
        } yield {
          assertTrue(resultsJoe == List(Postzio("Joe", "Hello")))
          assertTrue(resultsJack.isEmpty)
        }
      }
    }
  ) @@ TestAspect.sequential
}
