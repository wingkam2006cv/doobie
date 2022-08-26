// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import java.{util => ju}
import scala.collection.mutable
import scala.collection.immutable.Map
import scala.jdk.CollectionConverters._

package object compat {
  type =:=[From, To] = scala.=:=[From, To]

  def propertiesToScala(p: ju.Properties): Map[String, String] = p.asScala.toMap
  def mapToScala[K, V](m: ju.Map[K, V]): mutable.Map[K, V] = m.asScala
  def scalaToMap[K, V](m: Map[K, V]): ju.Map[K, V] = m.asJava
}
