/*
 * Copyright (c) Iván Molina Rebolledo 2023.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.skunkquill

import cats.effect.IO
import io.getquill.NamingStrategy
import natchez.Trace
import skunk.Session

/** A [[SkunkContext]] that uses [[cats.effect.IO]] as the effect type. This is a convenience class to avoid having to
  * specify the effect type when using Cats Effect IO.
  * @see
  *   [[SkunkContext]] for more information.
  */
class SkunkContextIO[+N <: NamingStrategy](
    override val naming: N,
    sessionSkunk: Session[IO]
)(implicit trace: Trace[IO])
    extends SkunkContext[N, IO](naming, sessionSkunk) {

  override type Result[T] = IO[T]
}
