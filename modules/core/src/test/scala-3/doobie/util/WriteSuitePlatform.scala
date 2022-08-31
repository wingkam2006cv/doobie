// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

trait WriteSuitePlatform { self: munit.FunSuite =>

  case class Woozle(a: (String, Int), b: Int *: String *: EmptyTuple, c: Boolean)

  test("Write should exist for some fancy types") {
    import doobie.generic.auto._

    Write[Woozle]
    Write[(Woozle, String)]
    Write[(Int, Woozle *: Woozle *: String *: EmptyTuple)]
  }

  test("Write should exist for option of some fancy types") {
    import doobie.generic.auto._

    Write[Option[Woozle]]
    Write[Option[(Woozle, String)]]
    Write[Option[(Int, Woozle *: Woozle *: String *: EmptyTuple)]]
  }

  test("derives") {
    case class Foo(a: String, b: Int) derives Write
  }
}
