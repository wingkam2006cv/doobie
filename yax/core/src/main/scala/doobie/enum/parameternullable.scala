package doobie.enum

import doobie.util.invariant._
import doobie.enum.{ nullability => N }

import java.sql.ParameterMetaData._

#+scalaz
import scalaz.Equal
import scalaz.std.anyVal.intInstance
#-scalaz
#+cats
import cats.kernel.Eq
import cats.kernel.std.int._
#-cats

object parameternullable {

  /** @group Implementation */
  sealed abstract class ParameterNullable(val toInt: Int) {
    def toNullability: N.Nullability =
      N.Nullability.fromParameterNullable(this) 
  }

  /** @group Values */ case object NoNulls         extends ParameterNullable(parameterNoNulls)
  /** @group Values */ case object Nullable        extends ParameterNullable(parameterNullable)
  /** @group Values */ case object NullableUnknown extends ParameterNullable(parameterNullableUnknown)

  /** @group Implementation */
  object ParameterNullable {

    def fromInt(n:Int): Option[ParameterNullable] =
      Some(n) collect {
        case NoNulls.toInt         => NoNulls
        case Nullable.toInt        => Nullable
        case NullableUnknown.toInt => NullableUnknown
      }

    def fromNullability(n: N.Nullability): ParameterNullable =
      n match {
        case N.NoNulls         => NoNulls
        case N.Nullable        => Nullable
        case N.NullableUnknown => NullableUnknown
      }

    def unsafeFromInt(n: Int): ParameterNullable =
      fromInt(n).getOrElse(throw InvalidOrdinal[ParameterNullable](n))

#+scalaz
    implicit val EqualParameterNullable: Equal[ParameterNullable] =
      Equal.equalBy(_.toInt)
#-scalaz
#+cats
    implicit val EqParameterNullable: Eq[ParameterNullable] =
      Eq.by(_.toInt)
#-cats

  }

}