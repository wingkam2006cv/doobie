// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.free

import cats.~>
import cats.effect.{ Async, ContextShift, ExitCase }
import cats.free.{ Free => FF } // alias because some algebras have an op called Free
import scala.concurrent.ExecutionContext
import com.github.ghik.silencer.silent

import java.io.InputStream
import java.io.Reader
import java.lang.Class
import java.lang.String
import java.math.BigDecimal
import java.net.URL
import java.sql.Blob
import java.sql.Clob
import java.sql.Connection
import java.sql.Date
import java.sql.NClob
import java.sql.ParameterMetaData
import java.sql.PreparedStatement
import java.sql.Ref
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.RowId
import java.sql.SQLType
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Time
import java.sql.Timestamp
import java.sql.{ Array => SqlArray }
import java.util.Calendar

@silent("deprecated")
object preparedstatement { module =>

  // Algebra of operations for PreparedStatement. Each accepts a visitor as an alternative to pattern-matching.
  sealed trait PreparedStatementOp[A] {
    def visit[F[_]](v: PreparedStatementOp.Visitor[F]): F[A]
  }

  // Free monad over PreparedStatementOp.
  type PreparedStatementIO[A] = FF[PreparedStatementOp, A]

  // Module of instances and constructors of PreparedStatementOp.
  object PreparedStatementOp {

    // Given a PreparedStatement we can embed a PreparedStatementIO program in any algebra that understands embedding.
    implicit val PreparedStatementOpEmbeddable: Embeddable[PreparedStatementOp, PreparedStatement] =
      new Embeddable[PreparedStatementOp, PreparedStatement] {
        def embed[A](j: PreparedStatement, fa: FF[PreparedStatementOp, A]) = Embedded.PreparedStatement(j, fa)
      }

    // Interface for a natural transformation PreparedStatementOp ~> F encoded via the visitor pattern.
    // This approach is much more efficient than pattern-matching for large algebras.
    trait Visitor[F[_]] extends (PreparedStatementOp ~> F) {
      final def apply[A](fa: PreparedStatementOp[A]): F[A] = fa.visit(this)

      // Common
      def raw[A](f: PreparedStatement => A): F[A]
      def embed[A](e: Embedded[A]): F[A]
      def delay[A](a: () => A): F[A]
      def handleErrorWith[A](fa: PreparedStatementIO[A], f: Throwable => PreparedStatementIO[A]): F[A]
      def raiseError[A](e: Throwable): F[A]
      def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A]
      def asyncF[A](k: (Either[Throwable, A] => Unit) => PreparedStatementIO[Unit]): F[A]
      def bracketCase[A, B](acquire: PreparedStatementIO[A])(use: A => PreparedStatementIO[B])(release: (A, ExitCase[Throwable]) => PreparedStatementIO[Unit]): F[B]
      def shift: F[Unit]
      def evalOn[A](ec: ExecutionContext)(fa: PreparedStatementIO[A]): F[A]

      // PreparedStatement
      def addBatch: F[Unit]
      def addBatch(a: String): F[Unit]
      def cancel: F[Unit]
      def clearBatch: F[Unit]
      def clearParameters: F[Unit]
      def clearWarnings: F[Unit]
      def close: F[Unit]
      def closeOnCompletion: F[Unit]
      def execute: F[Boolean]
      def execute(a: String): F[Boolean]
      def execute(a: String, b: Array[Int]): F[Boolean]
      def execute(a: String, b: Array[String]): F[Boolean]
      def execute(a: String, b: Int): F[Boolean]
      def executeBatch: F[Array[Int]]
      def executeLargeBatch: F[Array[Long]]
      def executeLargeUpdate: F[Long]
      def executeLargeUpdate(a: String): F[Long]
      def executeLargeUpdate(a: String, b: Array[Int]): F[Long]
      def executeLargeUpdate(a: String, b: Array[String]): F[Long]
      def executeLargeUpdate(a: String, b: Int): F[Long]
      def executeQuery: F[ResultSet]
      def executeQuery(a: String): F[ResultSet]
      def executeUpdate: F[Int]
      def executeUpdate(a: String): F[Int]
      def executeUpdate(a: String, b: Array[Int]): F[Int]
      def executeUpdate(a: String, b: Array[String]): F[Int]
      def executeUpdate(a: String, b: Int): F[Int]
      def getConnection: F[Connection]
      def getFetchDirection: F[Int]
      def getFetchSize: F[Int]
      def getGeneratedKeys: F[ResultSet]
      def getLargeMaxRows: F[Long]
      def getLargeUpdateCount: F[Long]
      def getMaxFieldSize: F[Int]
      def getMaxRows: F[Int]
      def getMetaData: F[ResultSetMetaData]
      def getMoreResults: F[Boolean]
      def getMoreResults(a: Int): F[Boolean]
      def getParameterMetaData: F[ParameterMetaData]
      def getQueryTimeout: F[Int]
      def getResultSet: F[ResultSet]
      def getResultSetConcurrency: F[Int]
      def getResultSetHoldability: F[Int]
      def getResultSetType: F[Int]
      def getUpdateCount: F[Int]
      def getWarnings: F[SQLWarning]
      def isCloseOnCompletion: F[Boolean]
      def isClosed: F[Boolean]
      def isPoolable: F[Boolean]
      def isWrapperFor(a: Class[_]): F[Boolean]
      def setArray(a: Int, b: SqlArray): F[Unit]
      def setAsciiStream(a: Int, b: InputStream): F[Unit]
      def setAsciiStream(a: Int, b: InputStream, c: Int): F[Unit]
      def setAsciiStream(a: Int, b: InputStream, c: Long): F[Unit]
      def setBigDecimal(a: Int, b: BigDecimal): F[Unit]
      def setBinaryStream(a: Int, b: InputStream): F[Unit]
      def setBinaryStream(a: Int, b: InputStream, c: Int): F[Unit]
      def setBinaryStream(a: Int, b: InputStream, c: Long): F[Unit]
      def setBlob(a: Int, b: Blob): F[Unit]
      def setBlob(a: Int, b: InputStream): F[Unit]
      def setBlob(a: Int, b: InputStream, c: Long): F[Unit]
      def setBoolean(a: Int, b: Boolean): F[Unit]
      def setByte(a: Int, b: Byte): F[Unit]
      def setBytes(a: Int, b: Array[Byte]): F[Unit]
      def setCharacterStream(a: Int, b: Reader): F[Unit]
      def setCharacterStream(a: Int, b: Reader, c: Int): F[Unit]
      def setCharacterStream(a: Int, b: Reader, c: Long): F[Unit]
      def setClob(a: Int, b: Clob): F[Unit]
      def setClob(a: Int, b: Reader): F[Unit]
      def setClob(a: Int, b: Reader, c: Long): F[Unit]
      def setCursorName(a: String): F[Unit]
      def setDate(a: Int, b: Date): F[Unit]
      def setDate(a: Int, b: Date, c: Calendar): F[Unit]
      def setDouble(a: Int, b: Double): F[Unit]
      def setEscapeProcessing(a: Boolean): F[Unit]
      def setFetchDirection(a: Int): F[Unit]
      def setFetchSize(a: Int): F[Unit]
      def setFloat(a: Int, b: Float): F[Unit]
      def setInt(a: Int, b: Int): F[Unit]
      def setLargeMaxRows(a: Long): F[Unit]
      def setLong(a: Int, b: Long): F[Unit]
      def setMaxFieldSize(a: Int): F[Unit]
      def setMaxRows(a: Int): F[Unit]
      def setNCharacterStream(a: Int, b: Reader): F[Unit]
      def setNCharacterStream(a: Int, b: Reader, c: Long): F[Unit]
      def setNClob(a: Int, b: NClob): F[Unit]
      def setNClob(a: Int, b: Reader): F[Unit]
      def setNClob(a: Int, b: Reader, c: Long): F[Unit]
      def setNString(a: Int, b: String): F[Unit]
      def setNull(a: Int, b: Int): F[Unit]
      def setNull(a: Int, b: Int, c: String): F[Unit]
      def setObject(a: Int, b: AnyRef): F[Unit]
      def setObject(a: Int, b: AnyRef, c: Int): F[Unit]
      def setObject(a: Int, b: AnyRef, c: Int, d: Int): F[Unit]
      def setObject(a: Int, b: AnyRef, c: SQLType): F[Unit]
      def setObject(a: Int, b: AnyRef, c: SQLType, d: Int): F[Unit]
      def setPoolable(a: Boolean): F[Unit]
      def setQueryTimeout(a: Int): F[Unit]
      def setRef(a: Int, b: Ref): F[Unit]
      def setRowId(a: Int, b: RowId): F[Unit]
      def setSQLXML(a: Int, b: SQLXML): F[Unit]
      def setShort(a: Int, b: Short): F[Unit]
      def setString(a: Int, b: String): F[Unit]
      def setTime(a: Int, b: Time): F[Unit]
      def setTime(a: Int, b: Time, c: Calendar): F[Unit]
      def setTimestamp(a: Int, b: Timestamp): F[Unit]
      def setTimestamp(a: Int, b: Timestamp, c: Calendar): F[Unit]
      def setURL(a: Int, b: URL): F[Unit]
      def setUnicodeStream(a: Int, b: InputStream, c: Int): F[Unit]
      def unwrap[T](a: Class[T]): F[T]

    }

    // Common operations for all algebras.
    final case class Raw[A](f: PreparedStatement => A) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raw(f)
    }
    final case class Embed[A](e: Embedded[A]) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.embed(e)
    }
    final case class Delay[A](a: () => A) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.delay(a)
    }
    final case class HandleErrorWith[A](fa: PreparedStatementIO[A], f: Throwable => PreparedStatementIO[A]) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.handleErrorWith(fa, f)
    }
    final case class RaiseError[A](e: Throwable) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raiseError(e)
    }
    final case class Async1[A](k: (Either[Throwable, A] => Unit) => Unit) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.async(k)
    }
    final case class AsyncF[A](k: (Either[Throwable, A] => Unit) => PreparedStatementIO[Unit]) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.asyncF(k)
    }
    final case class BracketCase[A, B](acquire: PreparedStatementIO[A], use: A => PreparedStatementIO[B], release: (A, ExitCase[Throwable]) => PreparedStatementIO[Unit]) extends PreparedStatementOp[B] {
      def visit[F[_]](v: Visitor[F]) = v.bracketCase(acquire)(use)(release)
    }
    final case object Shift extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.shift
    }
    final case class EvalOn[A](ec: ExecutionContext, fa: PreparedStatementIO[A]) extends PreparedStatementOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.evalOn(ec)(fa)
    }

    // PreparedStatement-specific operations.
    final case object AddBatch extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.addBatch
    }
    final case class  AddBatch1(a: String) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.addBatch(a)
    }
    final case object Cancel extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.cancel
    }
    final case object ClearBatch extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.clearBatch
    }
    final case object ClearParameters extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.clearParameters
    }
    final case object ClearWarnings extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.clearWarnings
    }
    final case object Close extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.close
    }
    final case object CloseOnCompletion extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.closeOnCompletion
    }
    final case object Execute extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.execute
    }
    final case class  Execute1(a: String) extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.execute(a)
    }
    final case class  Execute2(a: String, b: Array[Int]) extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.execute(a, b)
    }
    final case class  Execute3(a: String, b: Array[String]) extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.execute(a, b)
    }
    final case class  Execute4(a: String, b: Int) extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.execute(a, b)
    }
    final case object ExecuteBatch extends PreparedStatementOp[Array[Int]] {
      def visit[F[_]](v: Visitor[F]) = v.executeBatch
    }
    final case object ExecuteLargeBatch extends PreparedStatementOp[Array[Long]] {
      def visit[F[_]](v: Visitor[F]) = v.executeLargeBatch
    }
    final case object ExecuteLargeUpdate extends PreparedStatementOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.executeLargeUpdate
    }
    final case class  ExecuteLargeUpdate1(a: String) extends PreparedStatementOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.executeLargeUpdate(a)
    }
    final case class  ExecuteLargeUpdate2(a: String, b: Array[Int]) extends PreparedStatementOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.executeLargeUpdate(a, b)
    }
    final case class  ExecuteLargeUpdate3(a: String, b: Array[String]) extends PreparedStatementOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.executeLargeUpdate(a, b)
    }
    final case class  ExecuteLargeUpdate4(a: String, b: Int) extends PreparedStatementOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.executeLargeUpdate(a, b)
    }
    final case object ExecuteQuery extends PreparedStatementOp[ResultSet] {
      def visit[F[_]](v: Visitor[F]) = v.executeQuery
    }
    final case class  ExecuteQuery1(a: String) extends PreparedStatementOp[ResultSet] {
      def visit[F[_]](v: Visitor[F]) = v.executeQuery(a)
    }
    final case object ExecuteUpdate extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.executeUpdate
    }
    final case class  ExecuteUpdate1(a: String) extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.executeUpdate(a)
    }
    final case class  ExecuteUpdate2(a: String, b: Array[Int]) extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.executeUpdate(a, b)
    }
    final case class  ExecuteUpdate3(a: String, b: Array[String]) extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.executeUpdate(a, b)
    }
    final case class  ExecuteUpdate4(a: String, b: Int) extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.executeUpdate(a, b)
    }
    final case object GetConnection extends PreparedStatementOp[Connection] {
      def visit[F[_]](v: Visitor[F]) = v.getConnection
    }
    final case object GetFetchDirection extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getFetchDirection
    }
    final case object GetFetchSize extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getFetchSize
    }
    final case object GetGeneratedKeys extends PreparedStatementOp[ResultSet] {
      def visit[F[_]](v: Visitor[F]) = v.getGeneratedKeys
    }
    final case object GetLargeMaxRows extends PreparedStatementOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.getLargeMaxRows
    }
    final case object GetLargeUpdateCount extends PreparedStatementOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.getLargeUpdateCount
    }
    final case object GetMaxFieldSize extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getMaxFieldSize
    }
    final case object GetMaxRows extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getMaxRows
    }
    final case object GetMetaData extends PreparedStatementOp[ResultSetMetaData] {
      def visit[F[_]](v: Visitor[F]) = v.getMetaData
    }
    final case object GetMoreResults extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.getMoreResults
    }
    final case class  GetMoreResults1(a: Int) extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.getMoreResults(a)
    }
    final case object GetParameterMetaData extends PreparedStatementOp[ParameterMetaData] {
      def visit[F[_]](v: Visitor[F]) = v.getParameterMetaData
    }
    final case object GetQueryTimeout extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getQueryTimeout
    }
    final case object GetResultSet extends PreparedStatementOp[ResultSet] {
      def visit[F[_]](v: Visitor[F]) = v.getResultSet
    }
    final case object GetResultSetConcurrency extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getResultSetConcurrency
    }
    final case object GetResultSetHoldability extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getResultSetHoldability
    }
    final case object GetResultSetType extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getResultSetType
    }
    final case object GetUpdateCount extends PreparedStatementOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getUpdateCount
    }
    final case object GetWarnings extends PreparedStatementOp[SQLWarning] {
      def visit[F[_]](v: Visitor[F]) = v.getWarnings
    }
    final case object IsCloseOnCompletion extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isCloseOnCompletion
    }
    final case object IsClosed extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isClosed
    }
    final case object IsPoolable extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isPoolable
    }
    final case class  IsWrapperFor(a: Class[_]) extends PreparedStatementOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isWrapperFor(a)
    }
    final case class  SetArray(a: Int, b: SqlArray) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setArray(a, b)
    }
    final case class  SetAsciiStream(a: Int, b: InputStream) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setAsciiStream(a, b)
    }
    final case class  SetAsciiStream1(a: Int, b: InputStream, c: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setAsciiStream(a, b, c)
    }
    final case class  SetAsciiStream2(a: Int, b: InputStream, c: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setAsciiStream(a, b, c)
    }
    final case class  SetBigDecimal(a: Int, b: BigDecimal) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBigDecimal(a, b)
    }
    final case class  SetBinaryStream(a: Int, b: InputStream) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBinaryStream(a, b)
    }
    final case class  SetBinaryStream1(a: Int, b: InputStream, c: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBinaryStream(a, b, c)
    }
    final case class  SetBinaryStream2(a: Int, b: InputStream, c: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBinaryStream(a, b, c)
    }
    final case class  SetBlob(a: Int, b: Blob) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBlob(a, b)
    }
    final case class  SetBlob1(a: Int, b: InputStream) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBlob(a, b)
    }
    final case class  SetBlob2(a: Int, b: InputStream, c: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBlob(a, b, c)
    }
    final case class  SetBoolean(a: Int, b: Boolean) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBoolean(a, b)
    }
    final case class  SetByte(a: Int, b: Byte) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setByte(a, b)
    }
    final case class  SetBytes(a: Int, b: Array[Byte]) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setBytes(a, b)
    }
    final case class  SetCharacterStream(a: Int, b: Reader) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setCharacterStream(a, b)
    }
    final case class  SetCharacterStream1(a: Int, b: Reader, c: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setCharacterStream(a, b, c)
    }
    final case class  SetCharacterStream2(a: Int, b: Reader, c: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setCharacterStream(a, b, c)
    }
    final case class  SetClob(a: Int, b: Clob) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setClob(a, b)
    }
    final case class  SetClob1(a: Int, b: Reader) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setClob(a, b)
    }
    final case class  SetClob2(a: Int, b: Reader, c: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setClob(a, b, c)
    }
    final case class  SetCursorName(a: String) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setCursorName(a)
    }
    final case class  SetDate(a: Int, b: Date) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setDate(a, b)
    }
    final case class  SetDate1(a: Int, b: Date, c: Calendar) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setDate(a, b, c)
    }
    final case class  SetDouble(a: Int, b: Double) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setDouble(a, b)
    }
    final case class  SetEscapeProcessing(a: Boolean) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setEscapeProcessing(a)
    }
    final case class  SetFetchDirection(a: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setFetchDirection(a)
    }
    final case class  SetFetchSize(a: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setFetchSize(a)
    }
    final case class  SetFloat(a: Int, b: Float) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setFloat(a, b)
    }
    final case class  SetInt(a: Int, b: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setInt(a, b)
    }
    final case class  SetLargeMaxRows(a: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setLargeMaxRows(a)
    }
    final case class  SetLong(a: Int, b: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setLong(a, b)
    }
    final case class  SetMaxFieldSize(a: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setMaxFieldSize(a)
    }
    final case class  SetMaxRows(a: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setMaxRows(a)
    }
    final case class  SetNCharacterStream(a: Int, b: Reader) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNCharacterStream(a, b)
    }
    final case class  SetNCharacterStream1(a: Int, b: Reader, c: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNCharacterStream(a, b, c)
    }
    final case class  SetNClob(a: Int, b: NClob) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNClob(a, b)
    }
    final case class  SetNClob1(a: Int, b: Reader) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNClob(a, b)
    }
    final case class  SetNClob2(a: Int, b: Reader, c: Long) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNClob(a, b, c)
    }
    final case class  SetNString(a: Int, b: String) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNString(a, b)
    }
    final case class  SetNull(a: Int, b: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNull(a, b)
    }
    final case class  SetNull1(a: Int, b: Int, c: String) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNull(a, b, c)
    }
    final case class  SetObject(a: Int, b: AnyRef) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setObject(a, b)
    }
    final case class  SetObject1(a: Int, b: AnyRef, c: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setObject(a, b, c)
    }
    final case class  SetObject2(a: Int, b: AnyRef, c: Int, d: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setObject(a, b, c, d)
    }
    final case class  SetObject3(a: Int, b: AnyRef, c: SQLType) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setObject(a, b, c)
    }
    final case class  SetObject4(a: Int, b: AnyRef, c: SQLType, d: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setObject(a, b, c, d)
    }
    final case class  SetPoolable(a: Boolean) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setPoolable(a)
    }
    final case class  SetQueryTimeout(a: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setQueryTimeout(a)
    }
    final case class  SetRef(a: Int, b: Ref) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setRef(a, b)
    }
    final case class  SetRowId(a: Int, b: RowId) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setRowId(a, b)
    }
    final case class  SetSQLXML(a: Int, b: SQLXML) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setSQLXML(a, b)
    }
    final case class  SetShort(a: Int, b: Short) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setShort(a, b)
    }
    final case class  SetString(a: Int, b: String) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setString(a, b)
    }
    final case class  SetTime(a: Int, b: Time) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setTime(a, b)
    }
    final case class  SetTime1(a: Int, b: Time, c: Calendar) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setTime(a, b, c)
    }
    final case class  SetTimestamp(a: Int, b: Timestamp) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setTimestamp(a, b)
    }
    final case class  SetTimestamp1(a: Int, b: Timestamp, c: Calendar) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setTimestamp(a, b, c)
    }
    final case class  SetURL(a: Int, b: URL) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setURL(a, b)
    }
    final case class  SetUnicodeStream(a: Int, b: InputStream, c: Int) extends PreparedStatementOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setUnicodeStream(a, b, c)
    }
    final case class  Unwrap[T](a: Class[T]) extends PreparedStatementOp[T] {
      def visit[F[_]](v: Visitor[F]) = v.unwrap(a)
    }

  }
  import PreparedStatementOp._

  // Smart constructors for operations common to all algebras.
  val unit: PreparedStatementIO[Unit] = FF.pure[PreparedStatementOp, Unit](())
  def pure[A](a: A): PreparedStatementIO[A] = FF.pure[PreparedStatementOp, A](a)
  def raw[A](f: PreparedStatement => A): PreparedStatementIO[A] = FF.liftF(Raw(f))
  def embed[F[_], J, A](j: J, fa: FF[F, A])(implicit ev: Embeddable[F, J]): FF[PreparedStatementOp, A] = FF.liftF(Embed(ev.embed(j, fa)))
  def delay[A](a: => A): PreparedStatementIO[A] = FF.liftF(Delay(() => a))
  def handleErrorWith[A](fa: PreparedStatementIO[A], f: Throwable => PreparedStatementIO[A]): PreparedStatementIO[A] = FF.liftF[PreparedStatementOp, A](HandleErrorWith(fa, f))
  def raiseError[A](err: Throwable): PreparedStatementIO[A] = FF.liftF[PreparedStatementOp, A](RaiseError(err))
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): PreparedStatementIO[A] = FF.liftF[PreparedStatementOp, A](Async1(k))
  def asyncF[A](k: (Either[Throwable, A] => Unit) => PreparedStatementIO[Unit]): PreparedStatementIO[A] = FF.liftF[PreparedStatementOp, A](AsyncF(k))
  def bracketCase[A, B](acquire: PreparedStatementIO[A])(use: A => PreparedStatementIO[B])(release: (A, ExitCase[Throwable]) => PreparedStatementIO[Unit]): PreparedStatementIO[B] = FF.liftF[PreparedStatementOp, B](BracketCase(acquire, use, release))
  val shift: PreparedStatementIO[Unit] = FF.liftF[PreparedStatementOp, Unit](Shift)
  def evalOn[A](ec: ExecutionContext)(fa: PreparedStatementIO[A]) = FF.liftF[PreparedStatementOp, A](EvalOn(ec, fa))

  // Smart constructors for PreparedStatement-specific operations.
  val addBatch: PreparedStatementIO[Unit] = FF.liftF(AddBatch)
  def addBatch(a: String): PreparedStatementIO[Unit] = FF.liftF(AddBatch1(a))
  val cancel: PreparedStatementIO[Unit] = FF.liftF(Cancel)
  val clearBatch: PreparedStatementIO[Unit] = FF.liftF(ClearBatch)
  val clearParameters: PreparedStatementIO[Unit] = FF.liftF(ClearParameters)
  val clearWarnings: PreparedStatementIO[Unit] = FF.liftF(ClearWarnings)
  val close: PreparedStatementIO[Unit] = FF.liftF(Close)
  val closeOnCompletion: PreparedStatementIO[Unit] = FF.liftF(CloseOnCompletion)
  val execute: PreparedStatementIO[Boolean] = FF.liftF(Execute)
  def execute(a: String): PreparedStatementIO[Boolean] = FF.liftF(Execute1(a))
  def execute(a: String, b: Array[Int]): PreparedStatementIO[Boolean] = FF.liftF(Execute2(a, b))
  def execute(a: String, b: Array[String]): PreparedStatementIO[Boolean] = FF.liftF(Execute3(a, b))
  def execute(a: String, b: Int): PreparedStatementIO[Boolean] = FF.liftF(Execute4(a, b))
  val executeBatch: PreparedStatementIO[Array[Int]] = FF.liftF(ExecuteBatch)
  val executeLargeBatch: PreparedStatementIO[Array[Long]] = FF.liftF(ExecuteLargeBatch)
  val executeLargeUpdate: PreparedStatementIO[Long] = FF.liftF(ExecuteLargeUpdate)
  def executeLargeUpdate(a: String): PreparedStatementIO[Long] = FF.liftF(ExecuteLargeUpdate1(a))
  def executeLargeUpdate(a: String, b: Array[Int]): PreparedStatementIO[Long] = FF.liftF(ExecuteLargeUpdate2(a, b))
  def executeLargeUpdate(a: String, b: Array[String]): PreparedStatementIO[Long] = FF.liftF(ExecuteLargeUpdate3(a, b))
  def executeLargeUpdate(a: String, b: Int): PreparedStatementIO[Long] = FF.liftF(ExecuteLargeUpdate4(a, b))
  val executeQuery: PreparedStatementIO[ResultSet] = FF.liftF(ExecuteQuery)
  def executeQuery(a: String): PreparedStatementIO[ResultSet] = FF.liftF(ExecuteQuery1(a))
  val executeUpdate: PreparedStatementIO[Int] = FF.liftF(ExecuteUpdate)
  def executeUpdate(a: String): PreparedStatementIO[Int] = FF.liftF(ExecuteUpdate1(a))
  def executeUpdate(a: String, b: Array[Int]): PreparedStatementIO[Int] = FF.liftF(ExecuteUpdate2(a, b))
  def executeUpdate(a: String, b: Array[String]): PreparedStatementIO[Int] = FF.liftF(ExecuteUpdate3(a, b))
  def executeUpdate(a: String, b: Int): PreparedStatementIO[Int] = FF.liftF(ExecuteUpdate4(a, b))
  val getConnection: PreparedStatementIO[Connection] = FF.liftF(GetConnection)
  val getFetchDirection: PreparedStatementIO[Int] = FF.liftF(GetFetchDirection)
  val getFetchSize: PreparedStatementIO[Int] = FF.liftF(GetFetchSize)
  val getGeneratedKeys: PreparedStatementIO[ResultSet] = FF.liftF(GetGeneratedKeys)
  val getLargeMaxRows: PreparedStatementIO[Long] = FF.liftF(GetLargeMaxRows)
  val getLargeUpdateCount: PreparedStatementIO[Long] = FF.liftF(GetLargeUpdateCount)
  val getMaxFieldSize: PreparedStatementIO[Int] = FF.liftF(GetMaxFieldSize)
  val getMaxRows: PreparedStatementIO[Int] = FF.liftF(GetMaxRows)
  val getMetaData: PreparedStatementIO[ResultSetMetaData] = FF.liftF(GetMetaData)
  val getMoreResults: PreparedStatementIO[Boolean] = FF.liftF(GetMoreResults)
  def getMoreResults(a: Int): PreparedStatementIO[Boolean] = FF.liftF(GetMoreResults1(a))
  val getParameterMetaData: PreparedStatementIO[ParameterMetaData] = FF.liftF(GetParameterMetaData)
  val getQueryTimeout: PreparedStatementIO[Int] = FF.liftF(GetQueryTimeout)
  val getResultSet: PreparedStatementIO[ResultSet] = FF.liftF(GetResultSet)
  val getResultSetConcurrency: PreparedStatementIO[Int] = FF.liftF(GetResultSetConcurrency)
  val getResultSetHoldability: PreparedStatementIO[Int] = FF.liftF(GetResultSetHoldability)
  val getResultSetType: PreparedStatementIO[Int] = FF.liftF(GetResultSetType)
  val getUpdateCount: PreparedStatementIO[Int] = FF.liftF(GetUpdateCount)
  val getWarnings: PreparedStatementIO[SQLWarning] = FF.liftF(GetWarnings)
  val isCloseOnCompletion: PreparedStatementIO[Boolean] = FF.liftF(IsCloseOnCompletion)
  val isClosed: PreparedStatementIO[Boolean] = FF.liftF(IsClosed)
  val isPoolable: PreparedStatementIO[Boolean] = FF.liftF(IsPoolable)
  def isWrapperFor(a: Class[_]): PreparedStatementIO[Boolean] = FF.liftF(IsWrapperFor(a))
  def setArray(a: Int, b: SqlArray): PreparedStatementIO[Unit] = FF.liftF(SetArray(a, b))
  def setAsciiStream(a: Int, b: InputStream): PreparedStatementIO[Unit] = FF.liftF(SetAsciiStream(a, b))
  def setAsciiStream(a: Int, b: InputStream, c: Int): PreparedStatementIO[Unit] = FF.liftF(SetAsciiStream1(a, b, c))
  def setAsciiStream(a: Int, b: InputStream, c: Long): PreparedStatementIO[Unit] = FF.liftF(SetAsciiStream2(a, b, c))
  def setBigDecimal(a: Int, b: BigDecimal): PreparedStatementIO[Unit] = FF.liftF(SetBigDecimal(a, b))
  def setBinaryStream(a: Int, b: InputStream): PreparedStatementIO[Unit] = FF.liftF(SetBinaryStream(a, b))
  def setBinaryStream(a: Int, b: InputStream, c: Int): PreparedStatementIO[Unit] = FF.liftF(SetBinaryStream1(a, b, c))
  def setBinaryStream(a: Int, b: InputStream, c: Long): PreparedStatementIO[Unit] = FF.liftF(SetBinaryStream2(a, b, c))
  def setBlob(a: Int, b: Blob): PreparedStatementIO[Unit] = FF.liftF(SetBlob(a, b))
  def setBlob(a: Int, b: InputStream): PreparedStatementIO[Unit] = FF.liftF(SetBlob1(a, b))
  def setBlob(a: Int, b: InputStream, c: Long): PreparedStatementIO[Unit] = FF.liftF(SetBlob2(a, b, c))
  def setBoolean(a: Int, b: Boolean): PreparedStatementIO[Unit] = FF.liftF(SetBoolean(a, b))
  def setByte(a: Int, b: Byte): PreparedStatementIO[Unit] = FF.liftF(SetByte(a, b))
  def setBytes(a: Int, b: Array[Byte]): PreparedStatementIO[Unit] = FF.liftF(SetBytes(a, b))
  def setCharacterStream(a: Int, b: Reader): PreparedStatementIO[Unit] = FF.liftF(SetCharacterStream(a, b))
  def setCharacterStream(a: Int, b: Reader, c: Int): PreparedStatementIO[Unit] = FF.liftF(SetCharacterStream1(a, b, c))
  def setCharacterStream(a: Int, b: Reader, c: Long): PreparedStatementIO[Unit] = FF.liftF(SetCharacterStream2(a, b, c))
  def setClob(a: Int, b: Clob): PreparedStatementIO[Unit] = FF.liftF(SetClob(a, b))
  def setClob(a: Int, b: Reader): PreparedStatementIO[Unit] = FF.liftF(SetClob1(a, b))
  def setClob(a: Int, b: Reader, c: Long): PreparedStatementIO[Unit] = FF.liftF(SetClob2(a, b, c))
  def setCursorName(a: String): PreparedStatementIO[Unit] = FF.liftF(SetCursorName(a))
  def setDate(a: Int, b: Date): PreparedStatementIO[Unit] = FF.liftF(SetDate(a, b))
  def setDate(a: Int, b: Date, c: Calendar): PreparedStatementIO[Unit] = FF.liftF(SetDate1(a, b, c))
  def setDouble(a: Int, b: Double): PreparedStatementIO[Unit] = FF.liftF(SetDouble(a, b))
  def setEscapeProcessing(a: Boolean): PreparedStatementIO[Unit] = FF.liftF(SetEscapeProcessing(a))
  def setFetchDirection(a: Int): PreparedStatementIO[Unit] = FF.liftF(SetFetchDirection(a))
  def setFetchSize(a: Int): PreparedStatementIO[Unit] = FF.liftF(SetFetchSize(a))
  def setFloat(a: Int, b: Float): PreparedStatementIO[Unit] = FF.liftF(SetFloat(a, b))
  def setInt(a: Int, b: Int): PreparedStatementIO[Unit] = FF.liftF(SetInt(a, b))
  def setLargeMaxRows(a: Long): PreparedStatementIO[Unit] = FF.liftF(SetLargeMaxRows(a))
  def setLong(a: Int, b: Long): PreparedStatementIO[Unit] = FF.liftF(SetLong(a, b))
  def setMaxFieldSize(a: Int): PreparedStatementIO[Unit] = FF.liftF(SetMaxFieldSize(a))
  def setMaxRows(a: Int): PreparedStatementIO[Unit] = FF.liftF(SetMaxRows(a))
  def setNCharacterStream(a: Int, b: Reader): PreparedStatementIO[Unit] = FF.liftF(SetNCharacterStream(a, b))
  def setNCharacterStream(a: Int, b: Reader, c: Long): PreparedStatementIO[Unit] = FF.liftF(SetNCharacterStream1(a, b, c))
  def setNClob(a: Int, b: NClob): PreparedStatementIO[Unit] = FF.liftF(SetNClob(a, b))
  def setNClob(a: Int, b: Reader): PreparedStatementIO[Unit] = FF.liftF(SetNClob1(a, b))
  def setNClob(a: Int, b: Reader, c: Long): PreparedStatementIO[Unit] = FF.liftF(SetNClob2(a, b, c))
  def setNString(a: Int, b: String): PreparedStatementIO[Unit] = FF.liftF(SetNString(a, b))
  def setNull(a: Int, b: Int): PreparedStatementIO[Unit] = FF.liftF(SetNull(a, b))
  def setNull(a: Int, b: Int, c: String): PreparedStatementIO[Unit] = FF.liftF(SetNull1(a, b, c))
  def setObject(a: Int, b: AnyRef): PreparedStatementIO[Unit] = FF.liftF(SetObject(a, b))
  def setObject(a: Int, b: AnyRef, c: Int): PreparedStatementIO[Unit] = FF.liftF(SetObject1(a, b, c))
  def setObject(a: Int, b: AnyRef, c: Int, d: Int): PreparedStatementIO[Unit] = FF.liftF(SetObject2(a, b, c, d))
  def setObject(a: Int, b: AnyRef, c: SQLType): PreparedStatementIO[Unit] = FF.liftF(SetObject3(a, b, c))
  def setObject(a: Int, b: AnyRef, c: SQLType, d: Int): PreparedStatementIO[Unit] = FF.liftF(SetObject4(a, b, c, d))
  def setPoolable(a: Boolean): PreparedStatementIO[Unit] = FF.liftF(SetPoolable(a))
  def setQueryTimeout(a: Int): PreparedStatementIO[Unit] = FF.liftF(SetQueryTimeout(a))
  def setRef(a: Int, b: Ref): PreparedStatementIO[Unit] = FF.liftF(SetRef(a, b))
  def setRowId(a: Int, b: RowId): PreparedStatementIO[Unit] = FF.liftF(SetRowId(a, b))
  def setSQLXML(a: Int, b: SQLXML): PreparedStatementIO[Unit] = FF.liftF(SetSQLXML(a, b))
  def setShort(a: Int, b: Short): PreparedStatementIO[Unit] = FF.liftF(SetShort(a, b))
  def setString(a: Int, b: String): PreparedStatementIO[Unit] = FF.liftF(SetString(a, b))
  def setTime(a: Int, b: Time): PreparedStatementIO[Unit] = FF.liftF(SetTime(a, b))
  def setTime(a: Int, b: Time, c: Calendar): PreparedStatementIO[Unit] = FF.liftF(SetTime1(a, b, c))
  def setTimestamp(a: Int, b: Timestamp): PreparedStatementIO[Unit] = FF.liftF(SetTimestamp(a, b))
  def setTimestamp(a: Int, b: Timestamp, c: Calendar): PreparedStatementIO[Unit] = FF.liftF(SetTimestamp1(a, b, c))
  def setURL(a: Int, b: URL): PreparedStatementIO[Unit] = FF.liftF(SetURL(a, b))
  def setUnicodeStream(a: Int, b: InputStream, c: Int): PreparedStatementIO[Unit] = FF.liftF(SetUnicodeStream(a, b, c))
  def unwrap[T](a: Class[T]): PreparedStatementIO[T] = FF.liftF(Unwrap(a))

  // PreparedStatementIO is an Async
  implicit val AsyncPreparedStatementIO: Async[PreparedStatementIO] =
    new Async[PreparedStatementIO] {
      val asyncM = FF.catsFreeMonadForFree[PreparedStatementOp]
      def bracketCase[A, B](acquire: PreparedStatementIO[A])(use: A => PreparedStatementIO[B])(release: (A, ExitCase[Throwable]) => PreparedStatementIO[Unit]): PreparedStatementIO[B] = module.bracketCase(acquire)(use)(release)
      def pure[A](x: A): PreparedStatementIO[A] = asyncM.pure(x)
      def handleErrorWith[A](fa: PreparedStatementIO[A])(f: Throwable => PreparedStatementIO[A]): PreparedStatementIO[A] = module.handleErrorWith(fa, f)
      def raiseError[A](e: Throwable): PreparedStatementIO[A] = module.raiseError(e)
      def async[A](k: (Either[Throwable,A] => Unit) => Unit): PreparedStatementIO[A] = module.async(k)
      def asyncF[A](k: (Either[Throwable,A] => Unit) => PreparedStatementIO[Unit]): PreparedStatementIO[A] = module.asyncF(k)
      def flatMap[A, B](fa: PreparedStatementIO[A])(f: A => PreparedStatementIO[B]): PreparedStatementIO[B] = asyncM.flatMap(fa)(f)
      def tailRecM[A, B](a: A)(f: A => PreparedStatementIO[Either[A, B]]): PreparedStatementIO[B] = asyncM.tailRecM(a)(f)
      def suspend[A](thunk: => PreparedStatementIO[A]): PreparedStatementIO[A] = asyncM.flatten(module.delay(thunk))
    }

  // PreparedStatementIO is a ContextShift
  implicit val ContextShiftPreparedStatementIO: ContextShift[PreparedStatementIO] =
    new ContextShift[PreparedStatementIO] {
      def shift: PreparedStatementIO[Unit] = module.shift
      def evalOn[A](ec: ExecutionContext)(fa: PreparedStatementIO[A]) = module.evalOn(ec)(fa)
    }
}

