/*
 * Copyright (c) Iván Molina Rebolledo 2023.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.skunkquill

import com.ivmoreau.birdio.BirdIO
import io.getquill.NamingStrategy
import natchez.Trace
import skunk.Session
import com.twitter.util.Future

/** A [[SkunkContext]] that uses [[com.ivmoreau.birdio.BirdIO]] as the effect type. This is a convenience class to avoid
  * having to specify the effect type when using Twitter [[Future]]s.
  * @see
  *   [[SkunkContext]] for more information.
  */
class SkunkContextBirdIO[+N <: NamingStrategy](
    override val naming: N,
    sessionSkunk: Session[BirdIO]
)(implicit trace: Trace[BirdIO])
    extends SkunkContext[N, BirdIO](naming, sessionSkunk) {

  override type Result[T] = BirdIO[T]
}
