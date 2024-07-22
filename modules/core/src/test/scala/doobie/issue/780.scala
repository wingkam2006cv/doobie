// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.issue

import doobie._
import doobie.testutils.VoidExtensions

import scala.annotation.nowarn

@nowarn("msg=.*(Foo is never used|unused).*")
class `780` extends munit.FunSuite {
  import doobie.generic.auto._

  test("deriving instances should work correctly for Write from class scope") {
    class Foo[A: Write, B: Write] {
      Write[(A, B)].void
    }
  }

}
