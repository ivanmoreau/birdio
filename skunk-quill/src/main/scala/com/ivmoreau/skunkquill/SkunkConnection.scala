/*
 * Copyright (c) Iván Molina Rebolledo 2023.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.skunkquill

import cats.effect.{Async, IO as CatsIO}
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

  def sendQuery[Effect[_]: Async, T](
      queryStr: String
  ): Kleisli[Effect, Session[Effect], List[Row]] = {
    // We set isDynamic to true so that Skunk let us to manage the decoding in Quill
    // side. This allows [[anyDecoder]] to be used without specifying the type of the
    // query sql result.
    val query: Query[Void, Row] = sql"NOOP".query(anyDecoder).copy(isDynamic = true, sql = queryStr)
    Kleisli { (s: Session[Effect]) =>
      s.execute(query)
    }
  }

  def sendCommand[Effect[_]: Async](
      commandStr: String
  ): Kleisli[Effect, Session[Effect], Completion] = {
    val command: Command[Void] = sql"NOOP".command.copy(sql = commandStr)
    Kleisli { (s: Session[Effect]) =>
      s.execute(command)
    }
  }

}
