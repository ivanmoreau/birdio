package com.ivmoreau.skunkquill

import cats.effect.{IO, Resource}
import cats.effect.std.Console
import io.getquill.SnakeCase
import munit.CatsEffectSuite
import skunk.Session
import natchez.Trace.Implicits.noop

// Test table
case class Post(username: String, text: String)

class SkunkQuillTest extends CatsEffectSuite {

  val db: Resource[IO, SkunkContextIO[SnakeCase]] = {
    val session: Resource[IO, Session[IO]] =
      Session.single(
        host = "localhost",
        port = 5432,
        user = "test",
        database = "testpgdb",
        password = Some("test")
      )
    val newCtx = session
      .map(s => new SkunkContextIO[SnakeCase](SnakeCase, s))
    newCtx.flatMap(ctx =>
      Resource.make(IO(ctx))(ctx => {
        import ctx.*
        run(query[Post].delete).void // This also serves as a test that delete works LOL
      })
    )
  }

  test("insertion and selection works") {
    db.use { ctx =>
      import ctx.*
      for {
        _ <- run(query[Post].insertValue(Post("Joe", "Hello")))
        _ <- run(query[Post].insertValue(Post("Jack", "Hello")))
        result <- run(query[Post].filter(_.username == "Joe"))
      } yield assertEquals(result, List(Post("Joe", "Hello")))
    }
  }

  test("insertion and selection works 2") {
    db.use { ctx =>
      import ctx.*
      for {
        _ <- run(query[Post].insertValue(Post("Joe", "Hello")))
        _ <- run(query[Post].insertValue(Post("Jack", "Hello")))
        result <- run(query[Post].filter(_.username == "Jack"))
      } yield assertEquals(result, List(Post("Jack", "Hello")))
    }
  }

  test("transaction works") {
    db.use { ctx =>
      import ctx.*
      for {
        _ <- run(query[Post].insertValue(Post("Joe", "Hello")))
        _ <- transaction {
          for {
            _ <- run(query[Post].insertValue(Post("Joe", "Hello")))
            _ <- run(query[Post].insertValue(Post("Jack", "Hello")))
            _ <- IO.raiseError(new Exception("rollback"))
          } yield ()
        }.handleError(_ => ())
        resultsJoe <- run(query[Post].filter(_.username == "Joe"))
        resultsJack <- run(query[Post].filter(_.username == "Jack"))
      } yield {
        assertEquals(resultsJoe, List(Post("Joe", "Hello")))
        assert(resultsJack.isEmpty)
      }
    }
  }

  test("transactionTry works") {
    db.use { ctx =>
      import ctx.*
      for {
        _ <- run(query[Post].insertValue(Post("Joe", "Hello")))
        _ <- transactionTry {
          for {
            _ <- run(query[Post].insertValue(Post("Joe", "Hello")))
            _ <- run(query[Post].insertValue(Post("Jack", "Hello")))
            _ <- IO.raiseError(new Exception("rollback"))
          } yield ()
        }
        resultsJoe <- run(query[Post].filter(_.username == "Joe"))
        resultsJack <- run(query[Post].filter(_.username == "Jack"))
      } yield {
        assertEquals(resultsJoe, List(Post("Joe", "Hello")))
        assert(resultsJack.isEmpty)
      }
    }
  }
}
