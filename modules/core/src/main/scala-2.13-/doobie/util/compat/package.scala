// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import java.{util => ju}
import scala.collection.JavaConverters._
import scala.collection.immutable.Map

package object compat {
  type =:=[From, To] = scala.Predef.=:=[From, To]

  def propertiesToScala(p: ju.Properties): Map[String, String] = p.asScala.toMap
}
