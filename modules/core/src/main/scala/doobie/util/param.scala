// Copyright (c) 2013-2018 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import scala.annotation.implicitNotFound

import shapeless.{ HNil, HList, ::, Lazy}

/** Module defining the `Param` typeclass. */
object param {

  /**
   * Typeclass for a flat vector of `Put`s, analogous to `Write` but with no nesting or
   * generalization to product types. Each element expands to some nonzero number of `?`
   * placeholders in the SQL literal, and the param vector itself has a `Write` instance.
   */
  @implicitNotFound("""
Cannot construct a parameter vector of the following type:

  ${A}

Because one or more types therein (disregarding HNil) does not have a Put
instance in scope. Try them one by one in the REPL or in your code:

  scala> Put[Foo]

and find the one that has no instance, then construct one as needed. Refer to
Chapter 12 of the book of doobie for more information.
""")
  final class Param[A](val write: Write[A])

  /**
   * Derivations for `Param`, which disallow embedding. Each interpolated query argument corresponds
   * with a type with a `Put` instance, or an `Option` thereof.
   */
  object Param {

    def apply[A](implicit ev: Param[A]): Param[A] = ev

    /** Each `Put[A]` gives rise to a `Param[A]`. */
    implicit def fromPut[A: Put]: Param[A] =
      new Param[A](Write.fromPut[A])

    /** Each `Put[A]` gives rise to a `Param[Option[A]]`. */
    implicit def fromPutOption[A: Put]: Param[Option[A]] =
      new Param[Option[A]](Write.fromPutOption[A])

    /** There is an empty `Param` for `HNil`. */
    implicit val ParamHNil: Param[HNil] =
      new Param[HNil](Write.emptyProduct)

    /** Inductively we can cons a new `Param` onto the head of a `Param` of an `HList`. */
    implicit def ParamHList[H, T <: HList](implicit ph: Lazy[Param[H]], pt: Lazy[Param[T]]): Param[H :: T] =
      new Param[H :: T](Write.product[H,T](ph.value.write, pt.value.write))
  }

}
