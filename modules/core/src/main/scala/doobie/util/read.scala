// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import cats._
import doobie.free.{ FRS, ResultSetIO }
import doobie.enumerated.Nullability._
import java.sql.ResultSet
import scala.annotation.implicitNotFound

@implicitNotFound("""
Cannot find or construct a Read instance for type:

  ${A}

This can happen for a few reasons, but the most common case is that a data
member somewhere within this type doesn't have a Get instance in scope. Here are
some debugging hints:

- For Option types, ensure that a Read instance is in scope for the non-Option
  version.
- For types you expect to map to a single column ensure that a Get instance is
  in scope.
- For case classes, HLists, and shapeless records ensure that each element
  has a Read instance in scope.
- Lather, rinse, repeat, recursively until you find the problematic bit.

You can check that an instance exists for Read in the REPL or in your code:

  scala> Read[Foo]

and similarly with Get:

  scala> Get[Foo]

And find the missing instance and construct it as needed. Refer to Chapter 12
of the book of doobie for more information.
""")
final class Read[A](
  val gets: List[(Get[_], NullabilityKnown)],
  val unsafeGet: (ResultSet, Int) => A
) {

  final lazy val length: Int = gets.length

  def map[B](f: A => B): Read[B] =
      new Read(gets, (rs, n) => f(unsafeGet(rs, n)))

  def ap[B](ff: Read[A => B]): Read[B] =
    new Read(ff.gets ++ gets, (rs, n) => ff.unsafeGet(rs, n)(unsafeGet(rs, n + ff.length)))

  def get(n: Int): ResultSetIO[A] =
    FRS.raw(unsafeGet(_, n))

}

object Read extends ReadPlatform {

  def apply[A](implicit ev: Read[A]): ev.type = ev

  implicit val ReadApply: Applicative[Read] =
    new Applicative[Read] {
      def ap[A, B](ff: Read[A => B])(fa: Read[A]): Read[B] = fa.ap(ff)
      def pure[A](x: A): Read[A] = new Read(Nil, (_, _) => x)
      override def map[A, B](fa: Read[A])(f: A => B): Read[B] = fa.map(f)
    }

  implicit val unit: Read[Unit] =
    new Read(Nil, (_, _) => ())

  implicit def fromGet[A](implicit ev: Get[A]): Read[A] =
    new Read(List((ev, NoNulls)), ev.unsafeGetNonNullable)

  implicit def fromGetOption[A](implicit ev: Get[A]): Read[Option[A]] =
    new Read(List((ev, Nullable)), ev.unsafeGetNullable)

}
