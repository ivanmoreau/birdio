package com.ivmoreau.skunkquill

import cats.effect.Resource
import io.getquill.SnakeCase
import skunk.Session
import natchez.Trace.Implicits.noop
import com.ivmoreau.birdio.BirdIO
import com.twitter.util.Await

// Test table
case class Postbird(username: String, text: String)

class SkunkQuillBirdIOTest extends munit.FunSuite {

  val db: Resource[BirdIO, SkunkContextBirdIO[SnakeCase]] = {
    val session: Resource[BirdIO, Session[BirdIO]] =
      Session.single(
        host = "localhost",
        port = 5432,
        user = "test",
        database = "testpgdb",
        password = Some("test")
      )
    val newCtx = session
      .map(s => new SkunkContextBirdIO[SnakeCase](SnakeCase, s))
    newCtx.flatMap(ctx =>
      Resource.make(BirdIO(ctx))(ctx => {
        import ctx.*
        run(query[Postbird].delete).void // This also serves as a test that delete works LOL
      })
    )
  }

  test("insertion and selection works") {
    Await.result(
      db.use { ctx =>
        import ctx.*
        for {
          _ <- run(query[Postbird].insertValue(Postbird("Joe", "Hello")))
          _ <- run(query[Postbird].insertValue(Postbird("Jack", "Hello")))
          result <- run(query[Postbird].filter(_.username == "Joe"))
        } yield assertEquals(result, List(Postbird("Joe", "Hello")))
      }.run()
    )
  }

  test("insertion and selection works 2") {
    Await.result(
      db.use { ctx =>
        import ctx.*
        for {
          _ <- run(query[Postbird].insertValue(Postbird("Joe", "Hello")))
          _ <- run(query[Postbird].insertValue(Postbird("Jack", "Hello")))
          result <- run(query[Postbird].filter(_.username == "Jack"))
        } yield assertEquals(result, List(Postbird("Jack", "Hello")))
      }.run()
    )
  }

  test("transaction works") {
    Await.result(
      db.use { ctx =>
        import ctx.*
        for {
          _ <- run(query[Postbird].insertValue(Postbird("Joe", "Hello")))
          _ <- transaction {
            for {
              _ <- run(query[Postbird].insertValue(Postbird("Joe", "Hello")))
              _ <- run(query[Postbird].insertValue(Postbird("Jack", "Hello")))
              _ <- BirdIO.raiseError(new Exception("rollback"))
            } yield ()
          }.handleError(_ => ())
          resultsJoe <- run(query[Postbird].filter(_.username == "Joe"))
          resultsJack <- run(query[Postbird].filter(_.username == "Jack"))
        } yield {
          assertEquals(resultsJoe, List(Postbird("Joe", "Hello")))
          assert(resultsJack.isEmpty)
        }
      }.run()
    )
  }

  test("transactionTry works") {
    Await.result(
      db.use { ctx =>
        import ctx.*
        for {
          _ <- run(query[Postbird].insertValue(Postbird("Joe", "Hello")))
          _ <- transactionTry {
            for {
              _ <- run(query[Postbird].insertValue(Postbird("Joe", "Hello")))
              _ <- run(query[Postbird].insertValue(Postbird("Jack", "Hello")))
              _ <- BirdIO.raiseError(new Exception("rollback"))
            } yield ()
          }
          resultsJoe <- run(query[Postbird].filter(_.username == "Joe"))
          resultsJack <- run(query[Postbird].filter(_.username == "Jack"))
        } yield {
          assertEquals(resultsJoe, List(Postbird("Joe", "Hello")))
          assert(resultsJack.isEmpty)
        }
      }.run()
    )
  }
}
