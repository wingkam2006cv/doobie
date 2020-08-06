// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

private[util] object MultiVersionTypeSupport {
  type =:=[A, B] = scala.Predef.=:=[A, B]
}
