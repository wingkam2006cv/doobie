// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import doobie.enum.nullability._
import doobie.enum.parametermode._
import doobie.enum.jdbctype._
import doobie.util.meta._
import doobie.util.pretty._
import doobie.util.meta._

import scala.Predef._ // TODO: minimize

import cats.implicits._
import cats.data.Ior

/** Module defining a type for analyzing the type alignment of prepared statements. */
object analysis {

  /** Metadata for the JDBC end of a column/parameter mapping. */
  final case class ColumnMeta(jdbcType: JdbcType, vendorTypeName: String, nullability: Nullability, name: String)

  /** Metadata for the JDBC end of a column/parameter mapping. */
  final case class ParameterMeta(jdbcType: JdbcType, vendorTypeName: String, nullability: Nullability, mode: ParameterMode)

  sealed trait AlignmentError {
    def tag: String
    def index: Int
    def msg: String
  }

  final case class ParameterMisalignment(index: Int, alignment: Either[(Meta[_], NullabilityKnown), ParameterMeta]) extends AlignmentError {
    val tag = "P"
    override def msg = this match {
      case ParameterMisalignment(_, Left(_)) =>
        show"""|Interpolated value has no corresponding SQL parameter and likely appears inside a
            |comment or quoted string. Ior.Left will result in a runtime failure; fix this by removing
            |the parameter.""".stripMargin.lines.mkString(" ")
      case ParameterMisalignment(_, Right(pm)) =>
        show"""|${pm.jdbcType.show.toUpperCase} parameter is not set; this will result in a runtime
            |failure. Perhaps you used a literal ? rather than an interpolated value.""".stripMargin.lines.mkString(" ")
    }
  }

  final case class ParameterTypeError(index: Int, scalaType: Meta[_], n: NullabilityKnown, jdbcType: JdbcType, vendorTypeName: String, nativeMap: Map[String, JdbcType]) extends AlignmentError {
    override val tag = "P"
    override def msg =
      show"""|${typeName(scalaType, n)} is not coercible to ${jdbcType.show.toUpperCase}
          |(${vendorTypeName})
          |according to the JDBC specification.
          |Fix this by changing the schema type to ${scalaType.jdbcTarget.head.show.toUpperCase},
          |or the Scala type to ${Meta.writersOf(jdbcType, vendorTypeName).toList.map(typeName(_, n)).mkString(" or ")}.""".stripMargin.lines.mkString(" ")
  }

  final case class ColumnMisalignment(index: Int, alignment: Either[(Meta[_], NullabilityKnown), ColumnMeta]) extends AlignmentError {
    override val tag = "C"
    override def msg = this match {
      case ColumnMisalignment(_, Left((j, n))) =>
        show"""|Too few columns are selected, which will result in a runtime failure. Add a column or
            |remove mapped ${typeName(j, n)} from the result type.""".stripMargin.lines.mkString(" ")
      case ColumnMisalignment(_, Right(_)) =>
        show"""Column is unused. Remove it from the SELECT statement."""
    }
  }

  final case class NullabilityMisalignment(index: Int, name: String, st: Meta[_], jdk: NullabilityKnown, jdbc: NullabilityKnown) extends AlignmentError {
    override val tag = "C"
    override def msg = this match {
      // https://github.com/tpolecat/doobie/issues/164 ... NoNulls means "maybe no nulls"  :-\
      // case NullabilityMisalignment(i, name, st, NoNulls, Nullable) =>
      //   show"""Non-nullable column ${name.toUpperCase} is unnecessarily mapped to an Option type."""
      case NullabilityMisalignment(_, _, st, Nullable, NoNulls) =>
        show"""|Reading a NULL value into ${typeName(st, NoNulls)} will result in a runtime failure.
            |Fix this by making the schema type ${formatNullability(NoNulls)} or by changing the
            |Scala type to ${typeName(st, Nullable)}""".stripMargin.lines.mkString(" ")
      case _ => sys.error("unpossible, evidently")
    }
  }

  final case class ColumnTypeError(index: Int, jdk: Meta[_], n: NullabilityKnown, schema: ColumnMeta) extends AlignmentError {
    override val tag = "C"
    override def msg =
      Meta.readersOf(schema.jdbcType, schema.vendorTypeName).toList.map(typeName(_, n)) match {
        case Nil =>
          show"""|${schema.jdbcType.show.toUpperCase} (${schema.vendorTypeName}) is not
              |coercible to ${typeName(jdk, n)} according to the JDBC specification or any defined
              |mapping.
              |Fix this by changing the schema type to
              |${jdk.jdbcSource.list.map(_.show.toUpperCase).toList.mkString(" or ") }; or the
              |Scala type to an appropriate ${if (schema.jdbcType === Array) "array" else "object"}
              |type.
              |""".stripMargin.lines.mkString(" ")
        case ss =>
          show"""|${schema.jdbcType.show.toUpperCase} (${schema.vendorTypeName}) is not
              |coercible to ${typeName(jdk, n)} according to the JDBC specification or any defined
              |mapping.
              |Fix this by changing the schema type to
              |${jdk.jdbcSource.list.map(_.show.toUpperCase).toList.mkString(" or ") }, or the
              |Scala type to ${ss.mkString(" or ")}.
              |""".stripMargin.lines.mkString(" ")
      }
  }

  final case class ColumnTypeWarning(index: Int, jdk: Meta[_], n: NullabilityKnown, schema: ColumnMeta) extends AlignmentError {
    override val tag = "C"
    override def msg =
      show"""|${schema.jdbcType.show.toUpperCase} (${schema.vendorTypeName}) is ostensibly
          |coercible to ${typeName(jdk, n)}
          |according to the JDBC specification but is not a recommended target type. Fix this by
          |changing the schema type to
          |${jdk.jdbcSource.list.map(_.show.toUpperCase).toList.mkString(" or ") }; or the
          |Scala type to ${Meta.readersOf(schema.jdbcType, schema.vendorTypeName).toList.map(typeName(_, n)).mkString(" or ")}.
          |""".stripMargin.lines.mkString(" ")
  }

  /** Compatibility analysis for the given statement and aligned mappings. */
  final case class Analysis(
    sql:                String,
    nativeMap:          Map[String, JdbcType],
    parameterAlignment: List[(Meta[_], NullabilityKnown) Ior ParameterMeta],
    columnAlignment:    List[(Meta[_], NullabilityKnown) Ior ColumnMeta]) {

    def parameterMisalignments: List[ParameterMisalignment] =
      parameterAlignment.zipWithIndex.collect {
        case (Ior.Left(j), n) => ParameterMisalignment(n + 1, Left(j))
        case (Ior.Right(p), n) => ParameterMisalignment(n + 1, Right(p))
      }

    def parameterTypeErrors: List[ParameterTypeError] =
      parameterAlignment.zipWithIndex.collect {
        case (Ior.Both((j, n1), p), n) if !j.jdbcTarget.element(p.jdbcType) =>
          ParameterTypeError(n + 1, j, n1, p.jdbcType, p.vendorTypeName, nativeMap)
      }

    def columnMisalignments: List[ColumnMisalignment] =
      columnAlignment.zipWithIndex.collect {
        case (Ior.Left(j), n) => ColumnMisalignment(n + 1, Left(j))
        case (Ior.Right(p), n) => ColumnMisalignment(n + 1, Right(p))
      }

    def columnTypeErrors: List[ColumnTypeError] =
      columnAlignment.zipWithIndex.collect {
        case (Ior.Both((j, n1), p), n) if !(j.jdbcSource.list.toList ++ j.fold(_.jdbcSourceSecondary.toList, _ => Nil)).element(p.jdbcType) =>
          ColumnTypeError(n + 1, j, n1, p)
        case (Ior.Both((j, n1), p), n) if (p.jdbcType === JavaObject || p.jdbcType == Other) && !j.fold(_ => None, a => Some(a.schemaTypes.head)).element(p.vendorTypeName) =>
          ColumnTypeError(n + 1, j, n1, p)
      }

    def columnTypeWarnings: List[ColumnTypeWarning] =
      columnAlignment.zipWithIndex.collect {
        case (Ior.Both((j, n1), p), n) if j.fold(_.jdbcSourceSecondary.toList, _ => Nil).element(p.jdbcType) =>
          ColumnTypeWarning(n + 1, j, n1, p)
      }

    def nullabilityMisalignments: List[NullabilityMisalignment] =
      columnAlignment.zipWithIndex.collect {
        // We can't do anything helpful with NoNulls .. it means "might not be nullable"
        // case (Ior.Both((st, Nullable), ColumnMeta(_, _, NoNulls, col)), n) => NullabilityMisalignment(n + 1, col, st, NoNulls, Nullable)
        case (Ior.Both((st, NoNulls), ColumnMeta(_, _, Nullable, col)), n) => NullabilityMisalignment(n + 1, col, st, Nullable, NoNulls)
        // N.B. if we had a warning mechanism we could issue a warning for NullableUnknown
      }

    lazy val parameterAlignmentErrors =
      parameterMisalignments ++ parameterTypeErrors

    lazy val columnAlignmentErrors =
      columnMisalignments ++ columnTypeErrors ++ columnTypeWarnings ++ nullabilityMisalignments

    lazy val alignmentErrors =
      (parameterAlignmentErrors).sortBy(m => (m.index, m.msg)) ++
      (columnAlignmentErrors).sortBy(m => (m.index, m.msg))

    /** Description of each parameter, paired with its errors. */
    lazy val paramDescriptions: List[(String, List[AlignmentError])] = {
      val params: Block =
        parameterAlignment.zipWithIndex.map {
          case (Ior.Both((j1, n1), ParameterMeta(j2, s2, _, _)), i)  => List(f"P${i+1}%02d", show"${typeName(j1, n1)}", " → ", j2.show.toUpperCase, show"($s2)")
          case (Ior.Left((j1, n1)),                              i)  => List(f"P${i+1}%02d", show"${typeName(j1, n1)}", " → ", "", "")
          case (Ior.Right(          ParameterMeta(j2, s2, _, _)), i) => List(f"P${i+1}%02d", "",                     " → ", j2.show.toUpperCase, show"($s2)")
        } .transpose.map(Block(_)).foldLeft(Block(Nil))(_ leftOf1 _).trimLeft(1)
      params.toString.lines.toList.zipWithIndex.map { case (show, n) =>
        (show, parameterAlignmentErrors.filter(_.index == n + 1))
      }
    }

    /** Description of each parameter, paird with its errors. */
    lazy val columnDescriptions: List[(String, List[AlignmentError])] = {
      import pretty._
      val cols: Block =
        columnAlignment.zipWithIndex.map {
          case (Ior.Both((j1, n1), ColumnMeta(j2, s2, n2, m)), i)  => List(f"C${i+1}%02d", m, j2.show.toUpperCase, show"(${s2.toString})", formatNullability(n2), " → ", typeName(j1, n1))
          case (Ior.Left((j1, n1)),                            i)  => List(f"C${i+1}%02d", "",          "", "",                       "",                    " → ", typeName(j1, n1))
          case (Ior.Right(          ColumnMeta(j2, s2, n2, m)), i) => List(f"C${i+1}%02d", m, j2.show.toUpperCase, show"(${s2.toString})", formatNullability(n2), " → ", "")
        } .transpose.map(Block(_)).foldLeft(Block(Nil))(_ leftOf1 _).trimLeft(1)
      cols.toString.lines.toList.zipWithIndex.map { case (show, n) =>
        (show, columnAlignmentErrors.filter(_.index == n + 1))
      }
    }

  }

  // Some stringy helpers

  private val packagePrefix = "\\b[a-z]+\\.".r

  private def typeName(t: Meta[_], n: NullabilityKnown): String = {
    val name = packagePrefix.replaceAllIn(t.scalaType, "")
    n match {
      case NoNulls  => name
      case Nullable => show"Option[${name}]"
    }
  }

  private def formatNullability(n: Nullability): String =
    n match {
      case NoNulls         => "NOT NULL"
      case Nullable        => "NULL"
      case NullableUnknown => "NULL?"
    }


}
