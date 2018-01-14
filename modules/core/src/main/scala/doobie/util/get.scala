// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import cats.Functor
import cats.data.NonEmptyList
import cats.free.Coyoneda
import doobie.enum.JdbcType
import doobie.util.meta.Meta
import java.sql.ResultSet
import scala.reflect.runtime.universe.{ Type, TypeTag }

sealed abstract class Get[A](
  val typeStack: NonEmptyList[Option[Type]],
  val jdbcSources: NonEmptyList[JdbcType],
  val get: Coyoneda[(ResultSet, Int) => ?, A]
) {

  protected def mapImpl[B](f: A => B, typ: Option[Type]): Get[B]

  final def unsafeGetNonNullable(rs: ResultSet, n: Int): A = {
    val i = get.fi(rs, n)
    if (rs.wasNull)
      throw new RuntimeException("invariant violated; null observed in non-null get")
    get.k(i)
  }

  final def unsafeGetNullable(rs: ResultSet, n: Int): Option[A] = {
    val i = get.fi(rs, n)
    if (rs.wasNull) None else Some(get.k(i))
  }

  /**
   * Apply `f` to values retrieved by this `Get`. Prefer `tget`when possible because it will
   * allow for better diagnostics when checking queries. Note that `null` values will not be
   * transformed, so you do not need to (nor can you) handle this case.
   */
  final def map[B](f: A => B): Get[B] =
    mapImpl(f, None)

  /**
   * Equivalent to `map`, but accumulates the destination type in the type stack for improved
   * diagnostics. Prefer this method when you have concrete types or an available TypeTag.
   */
  final def tmap[B](f: A => B)(implicit ev: TypeTag[B]): Get[B] =
    mapImpl(f, Some(ev.tpe))

}

object Get extends GetInstances {

  def apply[A](implicit ev: Get[A]): ev.type = ev

  /** Get instance for a basic JDBC type. */
  final case class Basic[A](
    override val typeStack: NonEmptyList[Option[Type]],
    override val jdbcSources: NonEmptyList[JdbcType],
             val jdbcSourceSecondary: List[JdbcType],
    override val get: Coyoneda[(ResultSet, Int) => ?, A]
  ) extends Get[A](typeStack, jdbcSources, get) {

    protected def mapImpl[B](f: A => B, typ: Option[Type]): Get[B] =
      copy(get = get.map(f), typeStack = typ :: typeStack)

  }
  object Basic {

    def many[A](
      jdbcSources: NonEmptyList[JdbcType],
      jdbcSourceSecondary: List[JdbcType],
      get: (ResultSet, Int) => A,
    )(implicit ev: TypeTag[A]): Basic[A] =
      Basic(NonEmptyList.of(Some(ev.tpe)), jdbcSources, jdbcSourceSecondary, Coyoneda.lift(get))

    def one[A: TypeTag](
      jdbcSources: JdbcType,
      jdbcSourceSecondary: List[JdbcType],
      get: (ResultSet, Int) => A,
    ): Basic[A] =
      many(NonEmptyList.of(jdbcSources), jdbcSourceSecondary, get)

  }

  /** Get instance for an advanced JDBC type. */
  final case class Advanced[A](
    override val typeStack: NonEmptyList[Option[Type]],
    override val jdbcSources: NonEmptyList[JdbcType],
             val schemaTypes: NonEmptyList[Option[String]],
    override val get: Coyoneda[(ResultSet, Int) => ?, A]
  ) extends Get[A](typeStack, jdbcSources, get) {

    protected def mapImpl[B](f: A => B, typ: Option[Type]): Get[B] =
      copy(get = get.map(f), typeStack = typ :: typeStack)

  }
  object Advanced {

    def many[A](
      jdbcSources: NonEmptyList[JdbcType],
      schemaTypes: NonEmptyList[Option[String]],
      get: (ResultSet, Int) => A,
    )(implicit ev: TypeTag[A]): Advanced[A] =
      Advanced(NonEmptyList.of(Some(ev.tpe)), jdbcSources, schemaTypes, Coyoneda.lift(get))

    def one[A](
      jdbcSource: JdbcType,
      schemaTypes: NonEmptyList[Option[String]],
      get: (ResultSet, Int) => A,
    )(implicit ev: TypeTag[A]): Advanced[A] =
      Advanced(NonEmptyList.of(Some(ev.tpe)), NonEmptyList.of(jdbcSource), schemaTypes, Coyoneda.lift(get))

    @SuppressWarnings(Array("org.wartremover.warts.Equals", "org.wartremover.warts.AsInstanceOf"))
    def array[A >: Null <: AnyRef: TypeTag](schemaTypes: NonEmptyList[Option[String]]): Advanced[Array[A]] =
      one[Array[A]](JdbcType.Array, schemaTypes, (r, n) => {
          val a = r.getArray(n)
          (if (a == null) null else a.getArray).asInstanceOf[Array[A]]
        }
      )

  }

  /** An implicit Meta[A] means we also have an implicit Get[A]. */
  implicit def metaProjection[A](
    implicit m: Meta[A]
  ): Get[A] =
    m.get

}

trait GetInstances {

  implicit val FunctorPut: Functor[Get] =
    new Functor[Get] {
      def map[A, B](fa: Get[A])(f: A => B): Get[B] =
        fa.map(f)
    }

}