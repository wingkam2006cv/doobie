// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.free

import cats.effect.kernel.{ Async, Sync, MonadCancelThrow }
import doobie.WeakAsync

trait Types {
  /** @group Type Aliases - Free API */ type BlobIO[A]              = blob.BlobIO[A]
  /** @group Type Aliases - Free API */ type CallableStatementIO[A] = callablestatement.CallableStatementIO[A]
  /** @group Type Aliases - Free API */ type ClobIO[A]              = clob.ClobIO[A]
  /** @group Type Aliases - Free API */ type ConnectionIO[A]        = connection.ConnectionIO[A]
  /** @group Type Aliases - Free API */ type DatabaseMetaDataIO[A]  = databasemetadata.DatabaseMetaDataIO[A]
  /** @group Type Aliases - Free API */ type DriverIO[A]            = driver.DriverIO[A]
  /** @group Type Aliases - Free API */ type NClobIO[A]             = nclob.NClobIO[A]
  /** @group Type Aliases - Free API */ type PreparedStatementIO[A] = preparedstatement.PreparedStatementIO[A]
  /** @group Type Aliases - Free API */ type RefIO[A]               = ref.RefIO[A]
  /** @group Type Aliases - Free API */ type ResultSetIO[A]         = resultset.ResultSetIO[A]
  /** @group Type Aliases - Free API */ type SQLDataIO[A]           = sqldata.SQLDataIO[A]
  /** @group Type Aliases - Free API */ type SQLInputIO[A]          = sqlinput.SQLInputIO[A]
  /** @group Type Aliases - Free API */ type SQLOutputIO[A]         = sqloutput.SQLOutputIO[A]
  /** @group Type Aliases - Free API */ type StatementIO[A]         = statement.StatementIO[A]
}

trait Modules {
  /** @group Module Aliases - Free API */ lazy val FB   = blob
  /** @group Module Aliases - Free API */ lazy val FCS  = callablestatement
  /** @group Module Aliases - Free API */ lazy val FCL  = clob
  /** @group Module Aliases - Free API */ lazy val FC   = connection
  /** @group Module Aliases - Free API */ lazy val FDMD = databasemetadata
  /** @group Module Aliases - Free API */ lazy val FD   = driver
  /** @group Module Aliases - Free API */ lazy val FNCL = nclob
  /** @group Module Aliases - Free API */ lazy val FPS  = preparedstatement
  /** @group Module Aliases - Free API */ lazy val FREF = ref
  /** @group Module Aliases - Free API */ lazy val FRS  = resultset
  /** @group Module Aliases - Free API */ lazy val FSD  = sqldata
  /** @group Module Aliases - Free API */ lazy val FSI  = sqlinput
  /** @group Module Aliases - Free API */ lazy val FSO  = sqloutput
  /** @group Module Aliases - Free API */ lazy val FS   = statement
}

trait LowPriorityInstances2 {

  implicit def monadCancelThrowForWeakAsync[F[_]](implicit F: WeakAsync[F]): MonadCancelThrow[F] =
    WeakAsync.doobieMonadCancelForWeakAsync[F]
}

trait LowPriorityInstances1 extends LowPriorityInstances2 {
  
  implicit def syncForWeakAsync[F[_]](implicit F: WeakAsync[F]): Sync[F] =
     WeakAsync.doobieSyncForWeakAsync[F]

}

trait LowPriorityInstances extends LowPriorityInstances1 {
  import WeakAsync._

  /** @group Typeclass Instances */  implicit lazy val SyncBlobIO: Sync[BlobIO] =
    doobieSyncForWeakAsync(blob.WeakAsyncBlobIO)

  /** @group Typeclass Instances */  implicit lazy val SyncCallableStatementIO: Sync[CallableStatementIO] =
    doobieSyncForWeakAsync(callablestatement.WeakAsyncCallableStatementIO)

  /** @group Typeclass Instances */  implicit lazy val SyncClobIO: Sync[ClobIO] =
    doobieSyncForWeakAsync(clob.WeakAsyncClobIO)

  /** @group Typeclass Instances */  implicit lazy val SyncConnectionIO: Sync[ConnectionIO] =
    doobieSyncForWeakAsync(connection.WeakAsyncConnectionIO)

  /** @group Typeclass Instances */  implicit lazy val SyncDatabaseMetaDataIO: Sync[DatabaseMetaDataIO] =
    doobieSyncForWeakAsync(databasemetadata.WeakAsyncDatabaseMetaDataIO)

  /** @group Typeclass Instances */  implicit lazy val SyncDriverIO: Sync[DriverIO] =
    doobieSyncForWeakAsync(driver.WeakAsyncDriverIO)

  /** @group Typeclass Instances */  implicit lazy val SyncNClobIO: Sync[NClobIO] =
    doobieSyncForWeakAsync(nclob.WeakAsyncNClobIO)

  /** @group Typeclass Instances */  implicit lazy val SyncPreparedStatementIO: Sync[PreparedStatementIO] =
    doobieSyncForWeakAsync(preparedstatement.WeakAsyncPreparedStatementIO)

  /** @group Typeclass Instances */  implicit lazy val SyncRefIO: Sync[RefIO] =
    doobieSyncForWeakAsync(ref.WeakAsyncRefIO)

  /** @group Typeclass Instances */  implicit lazy val SyncResultSetIO: Sync[ResultSetIO] =
    doobieSyncForWeakAsync(resultset.WeakAsyncResultSetIO)

  /** @group Typeclass Instances */  implicit lazy val SyncSQLDataIO: Sync[SQLDataIO] =
    doobieSyncForWeakAsync(sqldata.WeakAsyncSQLDataIO)

  /** @group Typeclass Instances */  implicit lazy val SyncSQLInputIO: Sync[SQLInputIO] =
    doobieSyncForWeakAsync(sqlinput.WeakAsyncSQLInputIO)

  /** @group Typeclass Instances */  implicit lazy val SyncSQLOutputIO: Sync[SQLOutputIO] =
    doobieSyncForWeakAsync(sqloutput.WeakAsyncSQLOutputIO)

  /** @group Typeclass Instances */  implicit lazy val SyncStatementIO: Sync[StatementIO] =
    doobieSyncForWeakAsync(statement.WeakAsyncStatementIO)

}

trait Instances extends LowPriorityInstances {
  import WeakAsync._

  implicit def weakAsyncForAsync[F[_]](implicit F: Async[F]): WeakAsync[F] =
    doobieWeakAsyncForAsync[F]

  /** @group Typeclass Instances */  implicit lazy val MonadCancelBlobIO: MonadCancelThrow[BlobIO] =
    doobieMonadCancelForWeakAsync(blob.WeakAsyncBlobIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelCallableStatementIO: MonadCancelThrow[CallableStatementIO] =
    doobieMonadCancelForWeakAsync(callablestatement.WeakAsyncCallableStatementIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelClobIO: MonadCancelThrow[ClobIO] =
    doobieMonadCancelForWeakAsync(clob.WeakAsyncClobIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelConnectionIO: MonadCancelThrow[ConnectionIO] =
    doobieMonadCancelForWeakAsync(connection.WeakAsyncConnectionIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelDatabaseMetaDataIO: MonadCancelThrow[DatabaseMetaDataIO] =
    doobieMonadCancelForWeakAsync(databasemetadata.WeakAsyncDatabaseMetaDataIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelDriverIO: MonadCancelThrow[DriverIO] =
    doobieMonadCancelForWeakAsync(driver.WeakAsyncDriverIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelNClobIO: MonadCancelThrow[NClobIO] =
    doobieMonadCancelForWeakAsync(nclob.WeakAsyncNClobIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelPreparedStatementIO: MonadCancelThrow[PreparedStatementIO] =
    doobieMonadCancelForWeakAsync(preparedstatement.WeakAsyncPreparedStatementIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelRefIO: MonadCancelThrow[RefIO] =
    doobieMonadCancelForWeakAsync(ref.WeakAsyncRefIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelResultSetIO: MonadCancelThrow[ResultSetIO] =
    doobieMonadCancelForWeakAsync(resultset.WeakAsyncResultSetIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelSQLDataIO: MonadCancelThrow[SQLDataIO] =
    doobieMonadCancelForWeakAsync(sqldata.WeakAsyncSQLDataIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelSQLInputIO: MonadCancelThrow[SQLInputIO] =
    doobieMonadCancelForWeakAsync(sqlinput.WeakAsyncSQLInputIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelSQLOutputIO: MonadCancelThrow[SQLOutputIO] =
    doobieMonadCancelForWeakAsync(sqloutput.WeakAsyncSQLOutputIO)

  /** @group Typeclass Instances */  implicit lazy val MonadCancelStatementIO: MonadCancelThrow[StatementIO] =
    doobieMonadCancelForWeakAsync(statement.WeakAsyncStatementIO)

}
