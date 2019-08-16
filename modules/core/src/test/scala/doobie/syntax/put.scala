// Copyright (c) 2013-2018 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.syntax

import cats.implicits._
import doobie.implicits._
import org.specs2.mutable.Specification

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object putspec extends Specification {

  "put syntax" should {

    "convert to a fragment" in {
      fr"SELECT" ++ 1.fr
      true
    }

    "convert to a fragment0" in {
      fr"SELECT" ++ 1.fr0
      true
    }
    
    "convert an option to a fragment" in {
      fr"SELECT" ++ Some(1).fr
      true
    }

    "convert an option to a fragment0" in {
      fr"SELECT" ++ Some(1).fr0
      true
    }

    "work in a map" in {
      List(1, 2, 3).map(_.fr).combineAll
      true
    }

    "work in a map with fr0" in {
      List(1, 2, 3).map(_.fr0).combineAll
      true
    }

  }

}
