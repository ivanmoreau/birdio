package com.ivmoreau.skunkquill

import cats.effect.IO as CatsIO
import io.getquill.context.{Context, ContextVerbTranslate, ExecutionInfo}
import io.getquill.context.sql.SqlContext
import io.getquill.PostgresDialect
import io.getquill.NamingStrategy
import io.getquill.util.ContextLogger
import skunk.Session

import java.time.ZoneId
import scala.util.Try

final class SkunkContext[+N <: NamingStrategy](
    sessionSkunk: Session[CatsIO]
) extends Context[PostgresDialect, N]
    with ContextVerbTranslate
    with SqlContext[PostgresDialect, N]
    with Encoders
    with UUIDObjectEncoding {

  private val logger = ContextLogger(classOf[SkunkContext[_]])

  override type Session = Unit

  override type Result[T] = CatsIO[T]
  override type RunQueryResult[T] = Seq[T]
  override type RunQuerySingleResult[T] = T
  override type RunActionResult = Long
  override type RunActionReturningResult[T] = T
  override type RunBatchActionResult = Seq[Long]
  override type RunBatchActionReturningResult[T] = Seq[T]

  protected val dateTimeZone = ZoneId.systemDefault()

  override type NullChecker = SkunkNullChecker

  class SkunkNullChecker extends BaseNullChecker {
    override def apply(v1: Index, v2: Any): Boolean =
      v2 match {
        // Has more than one element?
        case product: Product =>
          product.productElement(v1) == null
        // Has only one element?
        case _ =>
          v2 == null
      }
  }

  implicit val nullChecker: NullChecker = new SkunkNullChecker()

  override def probe(statement: String): Try[_] = ???

  override def idiom: PostgresDialect = PostgresDialect

  override def naming: N = ???

  override def close(): Unit = ???

  override type Runner = Unit
  override type PrepareRow = Seq[Any]
  override type ResultRow = Any

  override type DecoderSqlType = SqlTypes.SqlTypes

  def prepareParams(
      statement: String,
      prepare: SkunkContext.this.Prepare
  ): Seq[String] =
    prepare(Nil, ())._2.map(prepareParam)

  def executeQuery[T](
      sql: String,
      prepare: Prepare = identityPrepare,
      extractor: Extractor[T] = ???
  )(
      info: ExecutionInfo,
      dc: Runner
  ): CatsIO[List[T]] = {
    val (params, values) = prepare(Nil, ())
    logger.logQuery(sql, params)
    ???
  }

}
