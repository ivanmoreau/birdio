/*
 * Copyright (C) zio-quill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivmoreau.skunkquill

object SqlTypes extends Enumeration {
  type SqlTypes = Value

  val BIT, TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL, CHAR, VARCHAR, LONGVARCHAR, DATE,
      TIME, TIMESTAMP, BINARY, VARBINARY, LONGVARBINARY, NULL, ARRAY, BLOB, BOOLEAN, TIME_WITH_TIMEZONE,
      TIMESTAMP_WITH_TIMEZONE, UUID = Value

}
