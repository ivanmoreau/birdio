/*
 * Copyright (c) Iván Molina Rebolledo 2023.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.skunkquill

import io.getquill.NamingStrategy
import natchez.Trace
import skunk.Session
import zio.Task
import zio.interop.catz.asyncInstance

/** A [[SkunkContext]] that uses [[zio.Task]] as the effect type. This is a convenience class to avoid having to specify
  * the effect type when using ZIO.
  * @see
  *   [[SkunkContext]] for more information.
  */
class SkunkContextZIO[+N <: NamingStrategy](
    override val naming: N,
    sessionSkunk: Session[Task]
)(implicit trace: Trace[Task])
    extends SkunkContext[N, Task](naming, sessionSkunk) {

  override type Result[T] = Task[T]
}
