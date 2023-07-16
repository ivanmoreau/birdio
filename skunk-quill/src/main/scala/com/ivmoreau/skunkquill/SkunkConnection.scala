package com.ivmoreau.skunkquill

import cats.effect.IO as CatsIO
import cats.data.Kleisli
import skunk.data.{Completion, Type}
import skunk.{Command, Decoder, Query, Session, Void}
import skunk.syntax.all.*

object SkunkConnection {

  type Row = List[Any]

  // Decoding will be handled by Quill
  val anyDecoder: Decoder[Row] = new Decoder[Row] {

    override def types: List[Type] = List()

    override def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, Row] =
      ss match {
        case Some(s) :: Nil     => Right(List(s))
        case None :: Nil        => Right(List(None))
        case Some(head) :: next => decode(offset + 1, next).map(head :: _)
        case None :: next       => decode(offset + 1, next).map(None :: _)
        case _                  => Left(Decoder.Error(offset, 1, "not enough columns"))
      }
  }

  def sendQuery[T](
      queryStr: String
  ): Kleisli[CatsIO, Session[CatsIO], List[Row]] = {
    // We set isDynamic to true so that Skunk let us to manage the decoding in Quill
    // side. This allows [[anyDecoder]] to be used without specifying the type of the
    // query sql result.
    val query: Query[Void, Row] = sql"NOOP".query(anyDecoder).copy(isDynamic = true, sql = queryStr)
    Kleisli { (s: Session[CatsIO]) =>
      s.execute(query)
    }
  }

  def sendCommand(
      commandStr: String
  ): Kleisli[CatsIO, Session[CatsIO], Completion] = {
    val command: Command[Void] = sql"NOOP".command.copy(sql = commandStr)
    Kleisli { (s: Session[CatsIO]) =>
      s.execute(command)
    }
  }

}
