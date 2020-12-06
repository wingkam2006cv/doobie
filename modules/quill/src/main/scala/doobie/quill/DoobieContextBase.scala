// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.quill

import cats.data.Nested
import cats.syntax.all._
import doobie._
import doobie.util.query.DefaultChunkSize
import fs2.Stream
import io.getquill.NamingStrategy
import io.getquill.context.sql.idiom.SqlIdiom
import io.getquill.context.StreamingContext
import java.sql.{ Connection }
import scala.util.Success
import doobie.enumerated.AutoGeneratedKeys
import io.getquill.ReturnAction.{ ReturnColumns, ReturnNothing, ReturnRecord }
import io.getquill.ReturnAction
import io.getquill.context.jdbc.JdbcContextBase
import io.getquill.util.ContextLogger

/** Base trait from which vendor-specific variants are derived. */
trait DoobieContextBase[Dialect <: SqlIdiom, Naming <: NamingStrategy]
  extends JdbcContextBase[Dialect, Naming]
     with StreamingContext[Dialect, Naming] {

  type Result[A]                        = ConnectionIO[A]
  type RunQueryResult[A]                = List[A]
  type RunQuerySingleResult[A]          = A
  type StreamResult[A]                  = Stream[ConnectionIO, A]
  type RunActionResult                  = Long
  type RunActionReturningResult[A]      = A
  type RunBatchActionResult             = List[Long]
  type RunBatchActionReturningResult[A] = List[A]

  // Logging behavior should be identical to JdbcContextBase.scala, which includes a couple calls
  // to log.underlying below.
  private val log: ContextLogger =
    new ContextLogger("DoobieContext")

  private def prepareAndLog(sql: String, p: Prepare): PreparedStatementIO[Unit] =
    FPS.raw(p).flatMap { case (params, _) =>
      FPS.delay(log.logQuery(sql, params))
    }

  override def executeQuery[A](
    sql:       String,
    prepare:   Prepare      = identityPrepare,
    extractor: Extractor[A] = identityExtractor
  ): ConnectionIO[List[A]] =
    HC.prepareStatement(sql) {
      prepareAndLog(sql, prepare) *>
      HPS.executeQuery {
        HRS.list(extractor)
      }
    }

  override def executeQuerySingle[A](
    sql:       String,
    prepare:   Prepare      = identityPrepare,
    extractor: Extractor[A] = identityExtractor
  ): ConnectionIO[A] =
    HC.prepareStatement(sql) {
      prepareAndLog(sql, prepare) *>
      HPS.executeQuery {
        HRS.getUnique(extractor)
      }
    }

  def streamQuery[A](
    fetchSize: Option[Int],
    sql:       String,
    prepare:   Prepare      = identityPrepare,
    extractor: Extractor[A] = identityExtractor
  ): Stream[ConnectionIO, A] =
    HC.stream(
      sql,
      prepareAndLog(sql, prepare),
      fetchSize.getOrElse(DefaultChunkSize)
    )(extractor)

  override def executeAction[A](
    sql:     String,
    prepare: Prepare = identityPrepare
  ): ConnectionIO[Long] =
    HC.prepareStatement(sql) {
      prepareAndLog(sql, prepare) *>
      HPS.executeUpdate.map(_.toLong)
    }

  private def prepareConnections[A](returningBehavior: ReturnAction) =
    returningBehavior match {
      case ReturnColumns(columns) =>
        (sql:String) => HC.prepareStatementS[A](sql, columns)(_)
      case ReturnRecord =>
        (sql:String) => HC.prepareStatement[A](sql, AutoGeneratedKeys.ReturnGeneratedKeys)(_)
      case ReturnNothing =>
        (sql:String) => HC.prepareStatement[A](sql)(_)
    }

  override def executeActionReturning[A](
    sql:               String,
    prepare:           Prepare = identityPrepare,
    extractor:         Extractor[A],
    returningBehavior: ReturnAction
  ): ConnectionIO[A] =
    prepareConnections[A](returningBehavior)(sql) {
      prepareAndLog(sql, prepare) *>
      FPS.executeUpdate *>
      HPS.getGeneratedKeys(HRS.getUnique(extractor))
    }

  private def prepareBatchAndLog(sql: String, p: Prepare): PreparedStatementIO[Unit] =
    FPS.raw(p) flatMap { case (params, _) =>
      FPS.delay(log.logBatchItem(sql, params))
    }

  override def executeBatchAction(
    groups: List[BatchGroup]
  ): ConnectionIO[List[Long]] =
    groups.flatTraverse { case BatchGroup(sql, preps) =>
      HC.prepareStatement(sql) {
        FPS.delay(log.underlying.debug("Batch: {}", sql)) *>
        preps.traverse(prepareBatchAndLog(sql, _) *> FPS.addBatch) *>
        Nested(HPS.executeBatch).map(_.toLong).value
      }
    }

  override def executeBatchActionReturning[A](
    groups:    List[BatchGroupReturning],
    extractor: Extractor[A]
  ): ConnectionIO[List[A]] =
    groups.flatTraverse { case BatchGroupReturning(sql, returningBehavior, preps) =>
      prepareConnections(returningBehavior)(sql) {
        FPS.delay(log.underlying.debug("Batch: {}", sql)) *>
        preps.traverse(prepareBatchAndLog(sql, _) *> FPS.addBatch) *>
        HPS.executeBatch *>
        HPS.getGeneratedKeys(HRS.list(extractor))
      }
    }

  // Turn an extractor into a `Read` so we can use the existing resultset.
  private implicit def extractorToRead[A](ex: Extractor[A]): Read[A] =
    new Read[A](Nil, (rs, _) => ex(rs))

  // Nothing to do here.
  override def close(): Unit = ()

  // Nothing to do here either.
  override def probe(statement: String) = Success(())

  // We can't implement this but it won't be called anyway so ¯\_(ツ)_/¯
  override protected def withConnection[A](f: Connection => ConnectionIO[A]): ConnectionIO[A] = ???

  protected val effect = null
}
