package com.ivmoreau.skunkquill

import cats.implicits.toBifunctorOps
import io.getquill.context.Context
import io.getquill.util.Messages.fail
import skunk.Decoder
import skunk.data.Type

import java.math.BigDecimal as JavaBigDecimal
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Date
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

trait Decoders {
  this: SkunkContext[_] =>

  type Decoder[T] = SkunkDecoder[T]

  type ResultRow = SkunkConnection.Row
  type Session = Unit

  type DecoderSqlType = skunk.data.Type

  case class SkunkDecoder[T](sqlType: DecoderSqlType)(implicit
      decoder: BaseDecoder[T]
  ) extends BaseDecoder[T] {
    override def apply(index: Index, row: ResultRow, session: Session) =
      decoder(index, row, session)
  }

  // Skunk result come in tuples, so this handles them using their Product
  // interface. If the result is not a tuple, it will be returned as is unless
  // the index is not 0, in which case it will fail.
  def getFromResultRow[T: ClassTag](row: ResultRow, index: Index): Any =
    row match {
      case row: Product            => row.productElement(index)
      case elem: Any if index == 0 => elem
      case other =>
        fail(
          s"Value '$other' at index $index doesn't exist in row of type '${classTag[T].runtimeClass}'"
        )
    }

  def decoder[T: ClassTag](
      f: PartialFunction[Any, T] = PartialFunction.empty,
      sqlType: DecoderSqlType
  ): Decoder[T] =
    SkunkDecoder[T](sqlType)(new BaseDecoder[T] {
      def apply(index: Index, row: ResultRow, session: Session) = {
        Try {
          row(index) match {
            case value: T                      => value
            case value if f.isDefinedAt(value) => f(value)
            case value =>
              fail(
                s"Value '$value' at index $index can't be decoded to '${classTag[T].runtimeClass}'"
              )
          }
        }.getOrElse(
          fail(
            s"Value at index $index can't be decoded to '${classTag[T].runtimeClass}'"
          )
        )
      }
    })

  // This is important
  implicit def mappedDecoder[I, O](implicit
      mapped: MappedEncoding[I, O],
      decoder: Decoder[I]
  ): Decoder[O] =
    SkunkDecoder(decoder.sqlType)(new BaseDecoder[O] {
      def apply(index: Index, row: ResultRow, session: Session): O =
        mapped.f(decoder.apply(index, row, session))
    })

  trait NumericDecoder[T] extends BaseDecoder[T] {

    def apply(index: Index, row: ResultRow, session: Session) =
      (getFromResultRow(row, index): Any) match {
        case v: Byte           => decode(v)
        case v: Short          => decode(v)
        case v: Int            => decode(v)
        case v: Long           => decode(v)
        case v: Float          => decode(v)
        case v: Double         => decode(v)
        case v: JavaBigDecimal => decode(v: BigDecimal)
        case other =>
          fail(
            s"Value $other is not numeric, type: ${other.getClass.getCanonicalName}"
          )
      }

    def decode[U](v: U)(implicit n: Numeric[U]): T
  }

  implicit def optionDecoder[T](implicit d: Decoder[T]): Decoder[Option[T]] =
    SkunkDecoder(d.sqlType)(new BaseDecoder[Option[T]] {
      def apply(index: Index, row: ResultRow, session: Session) =
        getFromResultRow(row, index) match {
          case null  => None
          case value => Some(d(index, row, session))
        }
    })

  implicit val stringDecoder: Decoder[String] =
    decoder[String](PartialFunction.empty, Type.varchar)

  implicit val bigDecimalDecoder: Decoder[BigDecimal] =
    SkunkDecoder(Type.numeric)(new NumericDecoder[BigDecimal] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        BigDecimal(n.toDouble(v))
    })

  implicit val booleanDecoder: Decoder[Boolean] =
    decoder[Boolean](
      {
        case v: Byte  => v == (1: Byte)
        case v: Short => v == (1: Short)
        case v: Int   => v == 1
        case v: Long  => v == 1L
      },
      Type.numeric
    )

  implicit val byteDecoder: Decoder[Byte] =
    decoder[Byte](
      { case v: Short =>
        v.toByte
      },
      Type.int2
    )

  implicit val shortDecoder: Decoder[Short] =
    decoder[Short](
      { case v: Byte =>
        v.toShort
      },
      Type.int2
    )

  implicit val intDecoder: Decoder[Int] =
    SkunkDecoder(Type.int4)(new NumericDecoder[Int] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toInt(v)
    })

  implicit val longDecoder: Decoder[Long] =
    SkunkDecoder(Type.int8)(new NumericDecoder[Long] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toLong(v)
    })

  implicit val floatDecoder: Decoder[Float] =
    SkunkDecoder(Type.float4)(new NumericDecoder[Float] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toFloat(v)
    })

  implicit val doubleDecoder: Decoder[Double] =
    SkunkDecoder(Type.float8)(new NumericDecoder[Double] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toDouble(v)
    })

  implicit val byteArrayDecoder: Decoder[Array[Byte]] =
    decoder[Array[Byte]](PartialFunction.empty, Type.bytea)

  implicit val dateDecoder: Decoder[Date] = decoder[Date](
    {
      case date: LocalDateTime =>
        Date.from(date.atZone(ZoneId.systemDefault()).toInstant)
      case date: LocalDate =>
        Date.from(date.atStartOfDay.atZone(ZoneId.systemDefault()).toInstant)
    },
    Type.timestamp
  )
  implicit val localDateDecoder: Decoder[LocalDate] =
    decoder[LocalDate](PartialFunction.empty, Type.date)
  implicit val localDateTimeDecoder: Decoder[LocalDateTime] =
    decoder[LocalDateTime](PartialFunction.empty, Type.timestamp)
}
