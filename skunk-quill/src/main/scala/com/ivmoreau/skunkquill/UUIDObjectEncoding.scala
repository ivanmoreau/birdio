package com.ivmoreau.skunkquill

import java.util.UUID

trait UUIDObjectEncoding {
  this: SkunkContext[_] =>

  implicit val uuidEncoder: Encoder[UUID] = encoder[UUID](SqlTypes.UUID)

  implicit val uuidDecoder: Decoder[UUID] =
    AsyncDecoder(SqlTypes.UUID)(
      (index: Index, row: ResultRow, session: Session) =>
        getFromResultRow(row, index) match {
          case value: UUID => value
        }
    )
}
