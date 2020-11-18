// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util.meta

import java.sql.{PreparedStatement, ResultSet}

import cats.Invariant
import cats.data.NonEmptyList
import doobie.enum.JdbcType
import doobie.util.{Get, Put}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

/**
 * Convenience for introducing a symmetric `Get`/`Put` pair into implicit scope, and for deriving
 * new symmetric pairs. It's important to understand that `Meta` should never be demanded by user
 * methods; instead demand both `Get` and `Put`. The reason for this is that while `Meta` implies
 * `Get` and `Put`, the presence of both `Get` and `Put` does *not* imply `Meta`.
 */
final class Meta[A](val get: Get[A], val put: Put[A]) {

  /** Meta is an invariant functor. Prefer `timap` as it provides for better diagnostics.  */
  def imap[B](f: A => B)(g: B => A): Meta[B] =
    new Meta(get.map(f), put.contramap(g))

  /** Variant of `imap` that takes a type tag, to aid in diagnostics. */
  def timap[B: TypeTag](f: A => B)(g: B => A): Meta[B] =
    new Meta(get.tmap(f), put.tcontramap(g))

}

/** Module of constructors and instances for `Meta`. */
object Meta extends MetaConstructors
               with MetaInstances
{

  /** Summon the `Meta` instance if possible. */
  def apply[A](implicit ev: Meta[A]): ev.type = ev

  /** @group Typeclass Instances */
  implicit val MetaInvariant: Invariant[Meta] =
    new Invariant[Meta] {
      def imap[A, B](fa: Meta[A])(f: A => B)(g: B => A) =
        fa.imap(f)(g)
    }

}

trait MetaConstructors {

  /**
   * Module of constructors for "basic" JDBC types.
   * @group Constructors
   */
  object Basic {

    def many[A: TypeTag](
      jdbcTarget: NonEmptyList[JdbcType],
      jdbcSource: NonEmptyList[JdbcType],
      jdbcSourceSecondary: List[JdbcType],
      get: (ResultSet, Int) => A,
      put: (PreparedStatement, Int, A) => Unit,
      update: (ResultSet, Int, A) => Unit
    ): Meta[A] =
      new Meta(
        Get.Basic.many(jdbcSource, jdbcSourceSecondary, get),
        Put.Basic.many(jdbcTarget, put, update)
      )

    def one[A: TypeTag](
      jdbcType: JdbcType,
      jdbcSourceSecondary: List[JdbcType],
      get: (ResultSet, Int) => A,
      put: (PreparedStatement, Int, A) => Unit,
      update: (ResultSet, Int, A) => Unit
    ): Meta[A] =
      new Meta(
        Get.Basic.one(jdbcType, jdbcSourceSecondary, get),
        Put.Basic.one(jdbcType, put, update)
      )

  }

  /**
   * Module of constructors for "advanced" JDBC types.
   * @group Constructors
   */
  object Advanced {

    def many[A: TypeTag](
      jdbcTypes: NonEmptyList[JdbcType],
      schemaTypes: NonEmptyList[String],
      get: (ResultSet, Int) => A,
      put: (PreparedStatement, Int, A) => Unit,
      update: (ResultSet, Int, A) => Unit
    ): Meta[A] =
      new Meta(
        Get.Advanced.many(jdbcTypes, schemaTypes, get),
        Put.Advanced.many(jdbcTypes, schemaTypes, put, update)
      )

    def one[A: TypeTag](
      jdbcTypes: JdbcType,
      schemaTypes: NonEmptyList[String],
      get: (ResultSet, Int) => A,
      put: (PreparedStatement, Int, A) => Unit,
      update: (ResultSet, Int, A) => Unit
    ): Meta[A] =
      new Meta(
        Get.Advanced.one(jdbcTypes, schemaTypes, get),
        Put.Advanced.one(jdbcTypes, schemaTypes, put, update)
      )

    def array[A >: Null <: AnyRef: TypeTag](
      elementType: String,
      schemaH: String,
      schemaT: String*
    ): Meta[Array[A]] =
      new Meta[Array[A]](
        Get.Advanced.array[A](NonEmptyList(schemaH, schemaT.toList)),
        Put.Advanced.array[A](NonEmptyList(schemaH, schemaT.toList), elementType)
      )

    def other[A >: Null <: AnyRef: TypeTag: ClassTag](
      schemaH: String,
      schemaT: String*
    ): Meta[A] =
      new Meta(
        Get.Advanced.other[A](NonEmptyList(schemaH, schemaT.toList)),
        Put.Advanced.other[A](NonEmptyList(schemaH, schemaT.toList))
      )

  }

}

trait MetaInstances { this: MetaConstructors =>
  import doobie.enum.JdbcType.{Boolean => JdbcBoolean, _}

  /** @group Instances */
  implicit val GetPutInvariant: Invariant[Meta] =
    new Invariant[Meta] {
      def imap[A, B](fa: Meta[A])(f: A => B)(g: B => A): Meta[B] =
        fa.imap(f)(g)
    }

  /** @group Instances */
  implicit val ByteMeta: Meta[Byte] =
    Basic.one[Byte](
      TinyInt,
      List(SmallInt, Integer, BigInt, Real, Float, Double, Decimal, Numeric, Bit, Char, VarChar,
        LongVarChar),
      _.getByte(_), _.setByte(_, _), _.updateByte(_, _))

  /** @group Instances */
  implicit val ShortMeta: Meta[Short] =
    Basic.one[Short](
      SmallInt,
      List(TinyInt, Integer, BigInt, Real, Float, Double, Decimal, Numeric, Bit, Char, VarChar,
        LongVarChar),
      _.getShort(_), _.setShort(_, _), _.updateShort(_, _))

  /** @group Instances */
  implicit val IntMeta: Meta[Int] =
    Basic.one[Int](
      Integer,
      List(TinyInt, SmallInt, BigInt, Real, Float, Double, Decimal, Numeric, Bit, Char, VarChar,
        LongVarChar),
      _.getInt(_), _.setInt(_, _), _.updateInt(_, _))

  /** @group Instances */
  implicit val LongMeta: Meta[Long] =
    Basic.one[Long](
      BigInt,
      List(TinyInt, Integer, SmallInt, Real, Float, Double, Decimal, Numeric, Bit, Char, VarChar,
        LongVarChar),
      _.getLong(_), _.setLong(_, _), _.updateLong(_, _))

  /** @group Instances */
  implicit val FloatMeta: Meta[Float] =
    Basic.one[Float](
      Real,
      List(TinyInt, Integer, SmallInt, BigInt, Float, Double, Decimal, Numeric, Bit, Char, VarChar,
        LongVarChar),
      _.getFloat(_), _.setFloat(_, _), _.updateFloat(_, _))

  /** @group Instances */
  implicit val DoubleMeta: Meta[Double] =
    Basic.many[Double](
      NonEmptyList.of(Double),
      NonEmptyList.of(Float, Double),
      List(TinyInt, Integer, SmallInt, BigInt, Float, Real, Decimal, Numeric, Bit, Char, VarChar,
        LongVarChar),
      _.getDouble(_), _.setDouble(_, _), _.updateDouble(_, _))

  /** @group Instances */
  implicit val BigDecimalMeta: Meta[java.math.BigDecimal] =
    Basic.many[java.math.BigDecimal](
      NonEmptyList.of(Numeric),
      NonEmptyList.of(Decimal, Numeric),
      List(TinyInt, Integer, SmallInt, BigInt, Float, Double, Real, Bit, Char, VarChar,
        LongVarChar),
      _.getBigDecimal(_), _.setBigDecimal(_, _), _.updateBigDecimal(_, _))

  /** @group Instances */
  implicit val BooleanMeta: Meta[Boolean] =
    Basic.many[Boolean](
      NonEmptyList.of(Bit, JdbcBoolean),
      NonEmptyList.of(Bit, JdbcBoolean),
      List(TinyInt, Integer, SmallInt, BigInt, Float, Double, Real, Decimal, Numeric, Char, VarChar,
        LongVarChar),
      _.getBoolean(_), _.setBoolean(_, _), _.updateBoolean(_, _))

  /** @group Instances */
  implicit val StringMeta: Meta[String] =
    Basic.many[String](
      NonEmptyList.of(VarChar, Char, LongVarChar, NChar, NVarChar, LongnVarChar),
      NonEmptyList.of(Char, VarChar, LongVarChar, NChar, NVarChar, LongnVarChar),
      List(TinyInt, Integer, SmallInt, BigInt, Float, Double, Real, Decimal, Numeric, Bit,
        Binary, VarBinary, LongVarBinary, Date, Time, Timestamp),
      _.getString(_), _.setString(_, _), _.updateString(_, _))

  /** @group Instances */
  implicit val ByteArrayMeta: Meta[Array[Byte]] =
    Basic.many[Array[Byte]](
      NonEmptyList.of(Binary, VarBinary, LongVarBinary),
      NonEmptyList.of(Binary, VarBinary),
      List(LongVarBinary),
      _.getBytes(_), _.setBytes(_, _), _.updateBytes(_, _))


  /** @group Instances */
  implicit val ScalaBigDecimalMeta: Meta[BigDecimal] =
    BigDecimalMeta.imap(BigDecimal.apply)(_.bigDecimal)

  import doobie.implicits.javasql.DateMeta

  /** @group Instances */
  implicit val JavaUtilDateMeta: Meta[java.util.Date] =
    DateMeta.imap[java.util.Date](a => a)(d => new java.sql.Date(d.getTime))

}
