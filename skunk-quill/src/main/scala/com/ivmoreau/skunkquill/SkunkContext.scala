/*
 * Copyright (c) Iván Molina Rebolledo 2023.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.skunkquill

import cats.data.Kleisli
import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFunctorOps}
import io.getquill.context.{Context, ContextVerbTranslate, ExecutionInfo}
import io.getquill.context.sql.SqlContext
import io.getquill.PostgresDialect
import io.getquill.NamingStrategy
import io.getquill.util.ContextLogger
import natchez.Trace
import skunk.Session
import skunk.data.Completion

import java.time.ZoneId
import scala.util.Try

/** A [[Context]] that uses Skunk as the underlying database connector. It's polymorphic over the effect type, so you
  * can use it with any effect type that has a [[cats.effect.Async]] instance and a [[natchez.Trace]] instance.
  *
  * If you don't need tracing, you can use [[natchez.Trace.Implicits.noop]] as the implicit trace instance, which
  * generates a no-op trace from your [[cats.effect.Async]] instance.
  *
  * @tparam N
  *   The naming strategy to use.
  * @tparam Effect
  *   The effect type to use.
  * @see
  *   [[SkunkContextIO]] for a convenience class that uses [[cats.effect.IO]] as the effect type.
  */
class SkunkContext[+N <: NamingStrategy, Effect[_]](
    val naming: N,
    sessionSkunk: Session[Effect]
)(implicit async: Async[Effect], trace: Trace[Effect])
    extends Context[PostgresDialect, N]
    with ContextVerbTranslate
    with SqlContext[PostgresDialect, N]
    with Decoders[Effect]
    with Encoders[Effect]
    with UUIDObjectEncoding[Effect] {

  private val logger = ContextLogger(classOf[SkunkContext[_, Effect]])

  override type Session = Unit

  override type Result[T] = Effect[T]
  override type RunQueryResult[T] = Seq[T]
  override type RunQuerySingleResult[T] = T
  override type RunActionResult = Completion
  override type RunActionReturningResult[T] = T
  override type RunBatchActionResult = Seq[Completion]
  override type RunBatchActionReturningResult[T] = Seq[T]

  protected val dateTimeZone = ZoneId.systemDefault()

  override type NullChecker = SkunkNullChecker

  class SkunkNullChecker extends BaseNullChecker {
    override def apply(v1: Index, v2: ResultRow): Boolean =
      Try(v2(v1)).map(_ => true).getOrElse(false)
  }

  implicit val nullChecker: NullChecker = new SkunkNullChecker()

  override def probe(statement: String): Try[_] = Try {}

  override def idiom: PostgresDialect = PostgresDialect

  override def close(): Unit = ()

  override type Runner = Unit
  override type PrepareRow = Seq[Any]
  override type ResultRow = SkunkConnection.Row

  override type DecoderSqlType = skunk.data.Type

  @inline
  def withSession[T](kleisli: Kleisli[Result, skunk.Session[Result], T]): Result[T] =
    kleisli(sessionSkunk)

  def withSessionDo[T]: (skunk.Session[Result] => Result[T]) => Result[T] =
    (f: skunk.Session[Result] => Result[T]) => f(sessionSkunk)

  def prepareParams(
      statement: String,
      prepare: SkunkContext.this.Prepare
  ): Seq[String] =
    prepare(Nil, ())._2.map(prepareParam)

  /** Executes the given function withing a transaction block, rolling back the transaction if failure occurs in
    * PostgreSQL. This does not catch exceptions thrown by the function, so the caller must handle them, for a function
    * that does not throw exceptions, use [[transactionTry]].
    */
  def transaction[T](f: Result[T]): Result[T] = withSessionDo { (session: skunk.Session[Result]) =>
    session.transaction.use { _ => f }
  }

  /** Executes the given function withing a transaction block, rolling back the transaction if failure occurs in
    * PostgreSQL. This catches exceptions thrown by the function, so the caller does not need to handle them, and if an
    * exception is thrown, the transaction is rolled back.
    * @see
    *   [[transaction]] for a version that does not catch exceptions.
    */
  def transactionTry[T](f: Result[T]): Result[Option[T]] = withSessionDo { (session: skunk.Session[Result]) =>
    session.transaction.use { t =>
      f.map[Option[T]](Some(_)).handleErrorWith { _ =>
        t.rollback >> Async[Result].pure(None) // We should probably log the error here
      }
    }
  }

  def executeQuery[T](
      sql: String,
      prepare: Prepare = identityPrepare,
      extractor: Extractor[T]
  )(
      info: ExecutionInfo,
      dc: Runner
  ): Result[List[T]] = {
    val (params, values) = prepare(Nil, ())
    logger.logQuery(sql, params)
    withSession {
      SkunkConnection.sendQuery(sql)
    }.map(_.map(extractor(_, ())))
  }

  def executeQuerySingle[T](
      sql: String,
      prepare: Prepare = identityPrepare,
      extractor: Extractor[T]
  )(info: ExecutionInfo, dc: Runner): Result[T] = {
    executeQuery(sql, prepare, extractor)(info, dc).map(handleSingleResult(sql, _))
  }

  def executeAction(
      sql: String,
      prepare: Prepare = identityPrepare
  )(info: ExecutionInfo, dc: Runner): Result[Completion] = {
    val (params, values) = prepare(Nil, ())
    logger.logQuery(sql, params)
    withSession {
      SkunkConnection.sendCommand(sql)
    }
  }

}
