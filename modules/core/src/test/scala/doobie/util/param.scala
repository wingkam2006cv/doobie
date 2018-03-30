// Copyright (c) 2013-2018 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie
package util

import shapeless._, shapeless.test._
import org.specs2.mutable.Specification

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object paramspec extends Specification {

  final case class Z(i: Int, s: String)
  object S

  "Param" should {

    "exist for HNil" in {
      Param[HNil]
      true
    }

    "exist for any Meta" in {
      def foo[A: Meta] = Param[A]
      true
    }

    "exist for any Option of Meta" in {
      def foo[A: Meta] = Param[Option[A]]
      true
    }


    "exist for any HList with Meta for head" in {
      def foo[A: Meta, B <: HList : Param] = Param[A :: B]
      true
    }

    "exist for any HList with Option of Meta for head" in {
      def foo[A: Meta, B <: HList:  Param] = Param[Option[A] :: B]
      true
    }

    "not exist for non-unary products" in {
      illTyped("Param[Z]")
      illTyped("Param[(Int, Int)]")
      illTyped("Param[S.type]")
      true
    }

  }

}
