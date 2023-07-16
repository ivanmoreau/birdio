package com.ivmoreau.skunkquill

import java.util.UUID

trait UUIDStringEncoding {
  this: SkunkContext[_] =>

  implicit val uuidEncoder: Encoder[UUID] =
    encoder[UUID]((v: UUID) => v.toString, SqlTypes.UUID)

  implicit val uuidDecoder: Decoder[UUID] =
    AsyncDecoder(SqlTypes.UUID)(
      (index: Index, row: ResultRow, session: Session) =>
        getFromResultRow(row, index) match {
          case value: String => UUID.fromString(value)
        }
    )
}
