// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie
package util

import cats.effect.IO
import doobie.util.stream.repeatEvalChunks
import org.scalacheck.Prop.forAll
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import scala.Predef._
import scala.util.Random


class processspec extends Specification with ScalaCheck {

  "repeatEvalChunks must" >> {

    "yield the same result irrespective of chunk size" ! forAll { (n0: Int) =>
      val dataSize  = 1000
      val chunkSize = (n0 % dataSize).abs max 1
      val data      = Seq.fill(dataSize)(Random.nextInt())
      val fa = {
        var temp = data
        IO {
          val (h, t) = temp.splitAt(chunkSize)
          temp = t
          h
        }
      }
      val result = repeatEvalChunks(fa).compile.toVector.unsafeRunSync()
      result must_== data
    }

  }

}
