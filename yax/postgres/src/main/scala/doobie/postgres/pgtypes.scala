package doobie.postgres

import doobie.enum.jdbctype
import doobie.imports._
import doobie.util.invariant._

import java.util.UUID
import java.net.InetAddress

import org.postgis._
import org.postgresql.util._
import org.postgresql.geometric._

import scala.Predef._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

/** `Meta` and `Atom` instances for PostgreSQL types. */
object pgtypes {

  // N.B. `Meta` is the lowest-level mapping and must always cope with NULL. Easy to forget.

  // Geometric Types, minus PGline which is "not fully implemented"
  implicit val PGboxType      = Meta.other[PGbox]("box")
  implicit val PGcircleType   = Meta.other[PGcircle]("circle")
  implicit val PGlsegType     = Meta.other[PGlseg]("lseg")
  implicit val PGpathType     = Meta.other[PGpath]("path")
  implicit val PGpointType    = Meta.other[PGpoint]("point")
  implicit val PGpolygonType  = Meta.other[PGpolygon]("polygon")

  // PGmoney doesn't seem to work:
  // PSQLException: : Bad value for type double : 1,234.56  (AbstractJdbc2ResultSet.java:3059)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.toDouble(AbstractJdbc2ResultSet.java:3059)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.getDouble(AbstractJdbc2ResultSet.java:2383)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.internalGetObject(AbstractJdbc2ResultSet.java:152)
  //   org.postgresql.jdbc3.AbstractJdbc3ResultSet.internalGetObject(AbstractJdbc3ResultSet.java:36)
  //   org.postgresql.jdbc4.AbstractJdbc4ResultSet.internalGetObject(AbstractJdbc4ResultSet.java:300)
  //   org.postgresql.jdbc2.AbstractJdbc2ResultSet.getObject(AbstractJdbc2ResultSet.java:2704)

  // Interval Type (TODO)
  // implicit val PGIntervalType = Meta.other[PGInterval]

  // UUID
  implicit val UuidType = Meta.other[UUID]("uuid")

  // Network Address Types
  implicit val InetType = Meta.other[PGobject]("inet").xmap[InetAddress](
    o => Option(o).map(a => InetAddress.getByName(a.getValue)).orNull,
    a => Option(a).map { a => 
      val o = new PGobject 
      o.setType("inet")
      o.setValue(a.getHostAddress)
      o
    } .orNull
  )

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

  // Construct a pair of Meta instances for arrays of lifted (nullable) and unlifted (non-
  // nullable) reference types (as noted above, PostgreSQL doesn't ship arrays of primitives). The
  // automatic lifting to Atom will give us lifted and unlifted arrays, for a total of four variants
  // of each 1-d array type. In the non-nullable case we simply check for nulls and perform a cast;
  // in the nullable case we must copy the array in both directions to lift/unlift Option.
  private def boxedPair[A >: Null <: AnyRef: ClassTag: TypeTag](elemType: String, arrayType: String, arrayTypeT: String*): (Meta[Array[A]], Meta[Array[Option[A]]]) = {
    val raw = Meta.array[A](elemType, arrayType, arrayTypeT: _*)
    // Ensure `a`, which may be null, which is ok, contains no null elements.
    def checkNull[B >: Null](a: Array[B], e: Exception): Array[B] =
      if (a == null) null else if (a.exists(_ == null)) throw e else a
    (raw.xmap(checkNull(_, NullableCellRead), checkNull(_, NullableCellUpdate)),
     raw.xmap[Array[Option[A]]](_.map(Option(_)), _.map(_.orNull).toArray))
  }

  // Arrays of lifted (nullable) and unlifted (non-nullable) Java wrapped primitives. PostgreSQL
  // does not seem to support tinyint[] (use a bytea instead) and smallint[] always arrives as Int[]
  // so you can xmap if you need Short[]. The type names provided here are what is reported by JDBC
  // when metadata is requested; there are numerous aliases but these are the ones we need. Nothing
  // about this is portable, sorry. (╯°□°）╯︵ ┻━┻
  implicit val (unliftedBooleanArrayType, liftedBooleanArrayType) = boxedPair[java.lang.Boolean]("bit",     "_bit")
  implicit val (unliftedIntegerArrayType, liftedIntegerArrayType) = boxedPair[java.lang.Integer]("int4",    "_int4")
  implicit val (unliftedLongArrayType,    liftedLongArrayType)    = boxedPair[java.lang.Long]   ("int8",    "_int8")
  implicit val (unliftedFloatArrayType,   liftedFloatArrayType)   = boxedPair[java.lang.Float]  ("float4",  "_float4")
  implicit val (unliftedDoubleArrayType,  liftedDoubleArrayType)  = boxedPair[java.lang.Double] ("float8",  "_float8")
  implicit val (unliftedStringArrayType,  liftedStringArrayType)  = boxedPair[java.lang.String] ("varchar", "_varchar", "_char", "_text", "_bpchar")
  implicit val (unliftedUUIDArrayType,    liftedUUIDArrayType)    = boxedPair[java.util.UUID]   ("uuid", "_uuid")

  // Unboxed equivalents (actually identical in the lifted case). We require that B is the unboxed
  // equivalent of A, otherwise this will fail in spectacular fashion, and we're using a cast in the
  // lifted case because the representation is identical, assuming no nulls. In the long run this
  // may need to become something slower but safer. Unclear.
  private def unboxedPair[A >: Null <: AnyRef: ClassTag, B <: AnyVal: ClassTag: TypeTag](f: A => B, g: B => A)(
    implicit boxed: Meta[Array[A]], boxedLifted: Meta[Array[Option[A]]]): (Meta[Array[B]], Meta[Array[Option[B]]]) =
    // TODO: assert, somehow, that A is the boxed version of B so we catch errors on instance
    // construction, which is somewhat better than at [logical] execution time.
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

  // PostGIS outer types
  implicit val PGgeometryType = Meta.other[PGgeometry]("geometry")
  implicit val PGbox3dType    = Meta.other[PGbox3d]("box3d")
  implicit val PGbox2dType    = Meta.other[PGbox2d]("box2d")

  // Constructor for geometry types via the `Geometry` member of PGgeometry
  private def geometryType[A >: Null <: Geometry: TypeTag](implicit A: ClassTag[A]): Meta[A] =
    PGgeometryType.nxmap[A](g =>
      try A.runtimeClass.cast(g.getGeometry).asInstanceOf[A]
      catch {
        case _: ClassCastException => throw InvalidObjectMapping(A.runtimeClass, g.getGeometry.getClass)
      }, new PGgeometry(_))

  // PostGIS Geometry Types
  implicit val GeometryType           = geometryType[Geometry]
  implicit val ComposedGeomType       = geometryType[ComposedGeom]
  implicit val GeometryCollectionType = geometryType[GeometryCollection]
  implicit val MultiLineStringType    = geometryType[MultiLineString]
  implicit val MultiPolygonType       = geometryType[MultiPolygon]
  implicit val PointComposedGeomType  = geometryType[PointComposedGeom]
  implicit val LineStringType         = geometryType[LineString]
  implicit val MultiPointType         = geometryType[MultiPoint]
  implicit val PolygonType            = geometryType[Polygon]
  implicit val PointType              = geometryType[Point]

  // It seems impossible to write a NULL value for an enum column/parameter using the current JDBC
  // driver, so we will define a mapping only for non-nullables. As a further twist, we read as 
  // String and write as PGobject. This Meta instance isn't able to write NULL. This would be a good
  // use case for splitting the read/write halves of Meta/Atom/Composite because here it's no
  // problem to *read* NULL, we just can't write it.
  private def enumPartialMeta(name: String): Meta[String] =
    Meta.basic1[String](jdbctype.Other, Nil,
      (rs, n) => rs.getString(n),
      (n, a) => FPS.setObject(n, {
        val o = new PGobject
        o.setValue(a.toString)
        o.setType(name)
        o
      }),
      (n, a) => FRS.updateObject(n, {
        val o = new PGobject
        o.setValue(a.toString)
        o.setType(name)
        o
      })
    )

  /** 
   * Construct an `Atom` for values of the given type, mapped via `String` to the named PostgreSQL
   * enum type.
   */
  def pgEnumString[A](name: String, f: String => A, g: A => String): Atom[A] =
#+scalaz
    Atom.fromScalaType(enumPartialMeta(name)).xmap[A](f, g)
#-scalaz
#+cats
    Atom.fromScalaType(enumPartialMeta(name)).imap[A](f)(g)
#-cats

  /** 
   * Construct an `Atom` for value members of the given `Enumeration`. Note that this precludes
   * reading or writing `Option[e.Value]` because writing NULL is unsupported by the driver.
   */
  def pgEnum(e: Enumeration, name: String): Atom[e.Value] =
    pgEnumString[e.Value](name, 
      a => try e.withName(a) catch { 
        case _: NoSuchElementException => throw InvalidEnum[e.Value](a) 
      }, _.toString)

  /** 
   * Construct an `Atom` for value members of the given Jave `enum`. Note that this precludes
   * reading or writing `Option[E]` because writing NULL is unsupported by the driver.
   */
  def pgJavaEnum[E <: java.lang.Enum[E]: TypeTag](name: String)(implicit E: ClassTag[E]): Atom[E] = {
    val clazz = E.runtimeClass.asInstanceOf[Class[E]]
    pgEnumString[E](name,
      a => try java.lang.Enum.valueOf(clazz, a).asInstanceOf[E] catch {
        case _: NoSuchElementException => throw InvalidEnum[E](a)
      }, _.name)
  }

}




