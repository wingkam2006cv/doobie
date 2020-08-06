// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.data.NonEmptyList
import cats.effect.{ IO, IOApp, ExitCode }
import doobie._
import doobie.enum.JdbcType
import doobie.implicits._
import org.postgresql.util._

/**
 * The normal string mapping doesn't work for enums defined in another schema. Here we have
 *
 *   CREATE TYPE returns_data.return_status AS ENUM (
 *     'INITIAL',
 *     'IN_PROGRESS',
 *     'FINISHED'
 *   );
 *
 */
object OtherSchema extends IOApp {

  // Ok this mapping goes via String when reading and PGObject when writing, and it understands
  // when the type is reported as OTHER (schemaType).
  def wackyPostgresMapping(schemaName: String): Meta[String] =
    Meta.Advanced.many[String](
      NonEmptyList.of(JdbcType.Other, JdbcType.VarChar),
      NonEmptyList.of(schemaName),
      (rs, n) => rs.getString(n),
      (ps, n, a) => {
        val o = new PGobject
        o.setValue(a.toString)
        o.setType(schemaName)
        ps.setObject(n, o)
      },
      (rs, n, a) => {
        val o = new PGobject
        o.setValue(a.toString)
        o.setType(schemaName)
        rs.updateObject(n, o)
      }
    )

  @SuppressWarnings(Array("org.wartremover.warts.Enumeration"))
  object ReturnStatus extends Enumeration {
    val INITIAL, IN_PROGRESS, FINISHED = Value
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  implicit val meta: Meta[ReturnStatus.Value] =
    wackyPostgresMapping(""""returns_data"."return_status"""").timap(ReturnStatus.withName)(_.toString)

  def run(args: List[String]): IO[ExitCode] = {

    // Some setup
    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver", "jdbc:postgresql:world", "postgres", ""
    )
    val y  = xa.yolo
    import y._

    // Check as column value only
    val q1 = sql"SELECT 'INITIAL'::returns_data.return_status".query[ReturnStatus.Value]
    val p1 = q1.check *> q1.unique.quick

    // Check as parameter too
    val q2 = sql"SELECT ${ReturnStatus.IN_PROGRESS}::returns_data.return_status".query[ReturnStatus.Value]
    val p2 = q2.check *> q2.unique.quick

    (p1 *> p2) as ExitCode.Success

  }

}
