package doobie.contrib.postgresql

import doobie.enum.jdbctype
import doobie.util.scalatype.ScalaType
import doobie.util.invariant._

import java.util.UUID
import java.net.InetAddress

import org.postgresql.util._
import org.postgresql.geometric._

import scala.Predef._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

import scalaz._, Scalaz._

/** `ScalaType` instances for PostgreSQL types. */
object pgtypes {

  // Geometric Types, minus PGline which is "not fully implemented"
  implicit val PGboxType      = ScalaType.objectType[PGbox]
  implicit val PGcircleType   = ScalaType.objectType[PGcircle]
  implicit val PGlsegType     = ScalaType.objectType[PGlseg]
  implicit val PGpathType     = ScalaType.objectType[PGpath]
  implicit val PGpointType    = ScalaType.objectType[PGpoint]
  implicit val PGpolygonType  = ScalaType.objectType[PGpolygon]

  // PGmoney doesn't seem to work:
  // PSQLException: : Bad value for type double : 1,234.56  (AbstractJdbc2ResultSet.java:3059)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.toDouble(AbstractJdbc2ResultSet.java:3059)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.getDouble(AbstractJdbc2ResultSet.java:2383)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.internalGetObject(AbstractJdbc2ResultSet.java:152)
  //   org.postgresql.jdbc3.AbstractJdbc3ResultSet.internalGetObject(AbstractJdbc3ResultSet.java:36)
  //   org.postgresql.jdbc4.AbstractJdbc4ResultSet.internalGetObject(AbstractJdbc4ResultSet.java:300)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.getObject(AbstractJdbc2ResultSet.java:2704)

  // Interval Type (TODO)
  // implicit val PGIntervalType = ScalaType.objectType[PGInterval]

  // UUID
  implicit val UuidType = ScalaType.objectType[UUID]

  // Network Address Types
  implicit val InetType = ScalaType.objectType[PGobject].xmap[InetAddress](
    o => Option(if (o == null) null else o.getValue).map(InetAddress.getByName).orNull,
    a => Option(a).map(a => new PGobject <| (_.setType("inet")) <| (_.setValue(a.getHostAddress))).orNull)

  // java.sql.Array::getArray returns an Object that may be of primitive type or of boxed type,
  // depending on the driver, so we can't really abstract over it. Also there's no telling what 
  // happens with multi-dimensional arrays since most databases don't support them. So anyway here 
  // we go with PostgreSQL support:
  //
  // PostgreSQL arrays show up as Array[AnyRef] with `null` for NULL, so that's mostly sensible;
  // there would be no way to distinguish 0 from NULL otherwise for an int[], for example. So,
  // these arrays can be multi-dimensional and can have NULL cells, but cannot have NULL slices;
  // i.e., {{1,2,3}, {4,5,NULL}} is ok but {{1,2,3}, NULL} is not. So this means we only have to 
  // worry about Array[Array[...[A]]] and Array[Array[...[Option[A]]]] in our mappings.

  // Construct a pair of ScalaType instances for arrays of lifted (nullable) and unlifted (non-
  // nullable) reference types (as noted above, PostgreSQL doesn't ship arrays of primitives). The
  // automatic lifting to Atom will give us lifted and unlifted arrays, for a total of four variants
  // of each 1-d array type. In the non-nullable case we simply check for nulls and perform a cast;
  // in the nullable case we must copy the array in both directions to lift/unlift Option.
  def boxedPair[A >: Null <: AnyRef: ClassTag: TypeTag](elemType: String): (ScalaType[Array[A]], ScalaType[Array[Option[A]]]) = {
    val raw = ScalaType.arrayType[A](elemType)
    def checkNull[A >: Null](a: Array[A], e: Exception): Array[A] =
      if (a == null) a else if (a.exists(_ == null)) throw e else a
    (raw.xmap(checkNull(_, NullableCellRead), checkNull(_, NullableCellUpdate)),
     raw.xmap[Array[Option[A]]](_.map(Option(_)), _.map(_.orNull).toArray))
  }

  // Arrays of lifted (nullable) and unlifted (non-nullable) Java wrapped primitives. PostgreSQL
  // does not seem to support tinyint[] (use a bytea instead) and smallint[] always arrives as Int[]
  // so you can xmap if you need Short[]. Note that the name of the element type is driver-specific 
  // and case-sensitive. (╯°□°）╯︵ ┻━┻ 
  implicit val (unliftedBooleanArrayType, liftedBooleanArrayType) = boxedPair[java.lang.Boolean]("bit")
  implicit val (unliftedIntegerArrayType, liftedIntegerArrayType) = boxedPair[java.lang.Integer]("integer")
  implicit val (unliftedLongArrayType,    liftedLongArrayType)    = boxedPair[java.lang.Long]   ("bigint")
  implicit val (unliftedFloatArrayType,   liftedFloatArrayType)   = boxedPair[java.lang.Float]  ("real")
  implicit val (unliftedDoubleArrayType,  liftedDoubleArrayType)  = boxedPair[java.lang.Double] ("double precision")
  implicit val (unliftedStringArrayType,  liftedStringArrayType)  = boxedPair[java.lang.String] ("varchar")

  // Unboxed equivalents (actually identical in the lifted case). We require that B is the unboxed
  // equivalent of A, otherwise this will fail in spectacular fashion, and we're using a cast in the
  // lifted case because the representation is identical, assuming no nulls. In the long run this 
  // may need to become something slower but safer. Unclear.
  private def unboxedPair[A >: Null <: AnyRef: ClassTag, B <: AnyVal: ClassTag](f: A => B, g: B => A)(
    implicit boxed: ScalaType[Array[A]], boxedLifted: ScalaType[Array[Option[A]]]): (ScalaType[Array[B]], ScalaType[Array[Option[B]]]) = 
    // TODO: assert, somehow, that A is the boxed version of B so we catch errors on instance 
    // construction, which is somewhat better than at [logical] execution time. :-\
    (boxed.xmap(a => if (a == null) null else a.map(f), a => if (a == null) null else a.map(g)),
     boxedLifted.xmap(_.asInstanceOf[Array[Option[B]]], _.asInstanceOf[Array[Option[A]]])) 

  // Arrays of lifted (nullable) and unlifted (non-nullable) AnyVals
  implicit val (unliftedUnboxedBooleanArrayType, liftedUnboxedBooleanArrayType) = unboxedPair[java.lang.Boolean, scala.Boolean](_.booleanValue, java.lang.Boolean.valueOf)
  implicit val (unliftedUnboxedIntegerArrayType, liftedUnboxedIntegerArrayType) = unboxedPair[java.lang.Integer, scala.Int]    (_.intValue,     java.lang.Integer.valueOf)
  implicit val (unliftedUnboxedLongArrayType,    liftedUnboxedLongArrayType)    = unboxedPair[java.lang.Long,    scala.Long]   (_.longValue,    java.lang.Long.valueOf)
  implicit val (unliftedUnboxedFloatArrayType,   liftedUnboxedFloatArrayType)   = unboxedPair[java.lang.Float,   scala.Float]  (_.floatValue,   java.lang.Float.valueOf)
  implicit val (unliftedUnboxedDoubleArrayType,  liftedUnboxedDoubleArrayType)  = unboxedPair[java.lang.Double,  scala.Double] (_.doubleValue,  java.lang.Double.valueOf)

  // So, it turns out that arrays of structs don't work because something is missing from the
  // implementation. So this means we will only be able to support primitive types for arrays.
  // 
  // java.sql.SQLFeatureNotSupportedException: Method org.postgresql.jdbc4.Jdbc4Array.getArrayImpl(long,int,Map) is not yet implemented.
  //   at org.postgresql.Driver.notImplemented(Driver.java:729)
  //   at org.postgresql.jdbc2.AbstractJdbc2Array.buildArray(AbstractJdbc2Array.java:771)
  //   at org.postgresql.jdbc2.AbstractJdbc2Array.getArrayImpl(AbstractJdbc2Array.java:171)
  //   at org.postgresql.jdbc2.AbstractJdbc2Array.getArray(AbstractJdbc2Array.java:128)

  // TODO: multidimensional arrays; in the worst case it's just copy/paste of everything above but
  // we can certainly do better than that.

}
