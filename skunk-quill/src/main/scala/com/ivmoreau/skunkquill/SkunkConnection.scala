package com.ivmoreau.skunkquill

import cats.effect.IO as CatsIO

import cats.data.Kleisli
import skunk.codec.all.varchar
import skunk.{Query, Session, Void}
import skunk.syntax.all.*

trait SkunkConnection {
  // protected def takeConnection[A, C <: ConcreteConnection]: (C => CatsIO[A]) => CatsIO[A]
  protected def takeConnection[A]
      : Kleisli[CatsIO, Session[CatsIO], A] => CatsIO[A]

  private[skunkquill] final def transaction[R <: SkunkConnection, A](
      action: CatsIO[A]
  ): CatsIO[A] =
    takeConnection[A] {
      Kleisli.ask[CatsIO, Session[CatsIO]].andThen {
        _.transaction.use { _ => action }
      }
    }

  /*
  private[skunkquill] final def sendQuery(query: String): CatsIO[QueryResult] =
    takeConnection((conn: ConcreteConnection) =>
      SkunkConnection.sendQuery(conn, query)
    )

  private[skunkquill] final def sendPreparedStatement(
      sql: String,
      params: Seq[Any]
  ): CatsIO[QueryResult] =
    takeConnection { (conn: ConcreteConnection) =>
      SkunkConnection.sendPreparedStatement(conn, sql, params)
    }*/

}

object SkunkConnection {

  def sendQuery(
      query: String
  ): Kleisli[CatsIO, Session[CatsIO], Any] = {
    val query: Query[Void, Any] = sql"SOME QUERY".query(varchar)
    Kleisli { (s: Session[CatsIO]) =>
      s.execute(query)
    }
  }

  /*
  def sendQuery(
      query: String
  ): C => CatsIO[QueryResult] =
    (conn: C) => CatsIO.fromCompletableFuture(CatsIO(conn.sendQuery(query)))

     def sendQuery[A](
      query: String
  ): Kleisli[CatsIO, Session[CatsIO], List[A]] =
    Kleisli { (s: Session[CatsIO]) =>
      s.execute(sql"""$text""".query(query))
    }

  def sendPreparedStatement[C <: ConcreteConnection](
      connection: C,
      sql: String,
      params: Seq[Any]
  ): CatsIO[QueryResult] =
    CatsIO.fromCompletableFuture(
      CatsIO(connection.sendPreparedStatement(sql, params.asJava))
    )

  def sendQuery[C <: ConcreteConnection](
      connection: C,
      query: String
  ): CatsIO[QueryResult] =
    CatsIO.fromCompletableFuture(CatsIO(connection.sendQuery(query)))

   */

}
