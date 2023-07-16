package com.ivmoreau.skunkquill

import cats.data.Kleisli
import cats.effect.IO as CatsIO
import io.getquill.context.{Context, ContextVerbTranslate, ExecutionInfo}
import io.getquill.context.sql.SqlContext
import io.getquill.PostgresDialect
import io.getquill.NamingStrategy
import io.getquill.util.ContextLogger
import skunk.Session
import skunk.data.Completion

import java.time.ZoneId
import scala.util.Try

final class SkunkContext[+N <: NamingStrategy](
    val naming: N,
    sessionSkunk: Session[CatsIO]
) extends Context[PostgresDialect, N]
    with ContextVerbTranslate
    with SqlContext[PostgresDialect, N]
    with Decoders
    with Encoders
    with UUIDObjectEncoding {

  private val logger = ContextLogger(classOf[SkunkContext[_]])

  override type Session = Unit

  override type Result[T] = CatsIO[T]
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
  def withSession[T](kleisli: Kleisli[CatsIO, skunk.Session[CatsIO], T]): CatsIO[T] =
    kleisli(sessionSkunk)

  def withSessionDo[T]: (skunk.Session[CatsIO] => CatsIO[T]) => CatsIO[T] =
    (f: skunk.Session[CatsIO] => CatsIO[T]) => f(sessionSkunk)

  def prepareParams(
      statement: String,
      prepare: SkunkContext.this.Prepare
  ): Seq[String] =
    prepare(Nil, ())._2.map(prepareParam)

  def transaction[T](f: CatsIO[T]): CatsIO[T] = withSessionDo { (session: skunk.Session[CatsIO]) =>
    session.transaction.use { t =>
      for {
        result <- f
        _ <- t.commit
      } yield result
    }
  }

  def executeQuery[T](
      sql: String,
      prepare: Prepare = identityPrepare,
      extractor: Extractor[T]
  )(
      info: ExecutionInfo,
      dc: Runner
  ): CatsIO[List[T]] = {
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
  )(info: ExecutionInfo, dc: Runner): CatsIO[T] = {
    executeQuery(sql, prepare, extractor)(info, dc).map(handleSingleResult(sql, _))
  }

  def executeAction(
      sql: String,
      prepare: Prepare = identityPrepare
  )(info: ExecutionInfo, dc: Runner): CatsIO[Completion] = {
    val (params, values) = prepare(Nil, ())
    logger.logQuery(sql, params)
    withSession {
      SkunkConnection.sendCommand(sql)
    }
  }

}
