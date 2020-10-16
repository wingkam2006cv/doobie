// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.free

import cats.~>
import cats.effect.{ Async, Cont, Fiber, LiftIO, Outcome, Poll, Sync }
import cats.effect.kernel.{ Deferred, Ref => CERef }
import cats.free.{ Free => FF } // alias because some algebras have an op called Free
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import java.lang.Class
import java.lang.String
import java.sql.Blob
import java.sql.CallableStatement
import java.sql.Clob
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.NClob
import java.sql.PreparedStatement
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Savepoint
import java.sql.Statement
import java.sql.Struct
import java.sql.{ Array => SqlArray }
import java.util.Map
import java.util.Properties
import java.util.concurrent.Executor
import cats.effect.IO

@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object connection { module =>

  // Algebra of operations for Connection. Each accepts a visitor as an alternative to pattern-matching.
  sealed trait ConnectionOp[A] {
    def visit[F[_]](v: ConnectionOp.Visitor[F]): F[A]
  }

  // Free monad over ConnectionOp.
  type ConnectionIO[A] = FF[ConnectionOp, A]

  // Module of instances and constructors of ConnectionOp.
  object ConnectionOp {

    // Given a Connection we can embed a ConnectionIO program in any algebra that understands embedding.
    implicit val ConnectionOpEmbeddable: Embeddable[ConnectionOp, Connection] =
      new Embeddable[ConnectionOp, Connection] {
        def embed[A](j: Connection, fa: FF[ConnectionOp, A]) = Embedded.Connection(j, fa)
      }

    // Interface for a natural transformation ConnectionOp ~> F encoded via the visitor pattern.
    // This approach is much more efficient than pattern-matching for large algebras.
    trait Visitor[F[_]] extends (ConnectionOp ~> F) {
      final def apply[A](fa: ConnectionOp[A]): F[A] = fa.visit(this)

      // Common
      def raw[A](f: Connection => A): F[A]
      def embed[A](e: Embedded[A]): F[A]
      def raiseError[A](e: Throwable): F[A]
      def handleErrorWith[A](fa: ConnectionIO[A])(f: Throwable => ConnectionIO[A]): F[A]
      def monotonic: F[FiniteDuration]
      def realTime: F[FiniteDuration]
      def delay[A](thunk: => A): F[A]
      def suspend[A](hint: Sync.Type)(thunk: => A): F[A]
      def forceR[A, B](fa: ConnectionIO[A])(fb: ConnectionIO[B]): F[B]
      def uncancelable[A](body: Poll[ConnectionIO] => ConnectionIO[A]): F[A]
      def poll[A](poll: Any, fa: ConnectionIO[A]): F[A]
      def canceled: F[Unit]
      def onCancel[A](fa: ConnectionIO[A], fin: ConnectionIO[Unit]): F[A]
      def cede: F[Unit]
      def ref[A](a: A): F[CERef[ConnectionIO, A]]
      def deferred[A]: F[Deferred[ConnectionIO, A]]
      def sleep(time: FiniteDuration): F[Unit]
      def evalOn[A](fa: ConnectionIO[A], ec: ExecutionContext): F[A]
      def executionContext: F[ExecutionContext]
      def async[A](k: (Either[Throwable, A] => Unit) => ConnectionIO[Option[ConnectionIO[Unit]]]): F[A]
      def liftIO[A](ioa: IO[A]): F[A]

      // Connection
      def abort(a: Executor): F[Unit]
      def clearWarnings: F[Unit]
      def close: F[Unit]
      def commit: F[Unit]
      def createArrayOf(a: String, b: Array[AnyRef]): F[SqlArray]
      def createBlob: F[Blob]
      def createClob: F[Clob]
      def createNClob: F[NClob]
      def createSQLXML: F[SQLXML]
      def createStatement: F[Statement]
      def createStatement(a: Int, b: Int): F[Statement]
      def createStatement(a: Int, b: Int, c: Int): F[Statement]
      def createStruct(a: String, b: Array[AnyRef]): F[Struct]
      def getAutoCommit: F[Boolean]
      def getCatalog: F[String]
      def getClientInfo: F[Properties]
      def getClientInfo(a: String): F[String]
      def getHoldability: F[Int]
      def getMetaData: F[DatabaseMetaData]
      def getNetworkTimeout: F[Int]
      def getSchema: F[String]
      def getTransactionIsolation: F[Int]
      def getTypeMap: F[Map[String, Class[_]]]
      def getWarnings: F[SQLWarning]
      def isClosed: F[Boolean]
      def isReadOnly: F[Boolean]
      def isValid(a: Int): F[Boolean]
      def isWrapperFor(a: Class[_]): F[Boolean]
      def nativeSQL(a: String): F[String]
      def prepareCall(a: String): F[CallableStatement]
      def prepareCall(a: String, b: Int, c: Int): F[CallableStatement]
      def prepareCall(a: String, b: Int, c: Int, d: Int): F[CallableStatement]
      def prepareStatement(a: String): F[PreparedStatement]
      def prepareStatement(a: String, b: Array[Int]): F[PreparedStatement]
      def prepareStatement(a: String, b: Array[String]): F[PreparedStatement]
      def prepareStatement(a: String, b: Int): F[PreparedStatement]
      def prepareStatement(a: String, b: Int, c: Int): F[PreparedStatement]
      def prepareStatement(a: String, b: Int, c: Int, d: Int): F[PreparedStatement]
      def releaseSavepoint(a: Savepoint): F[Unit]
      def rollback: F[Unit]
      def rollback(a: Savepoint): F[Unit]
      def setAutoCommit(a: Boolean): F[Unit]
      def setCatalog(a: String): F[Unit]
      def setClientInfo(a: Properties): F[Unit]
      def setClientInfo(a: String, b: String): F[Unit]
      def setHoldability(a: Int): F[Unit]
      def setNetworkTimeout(a: Executor, b: Int): F[Unit]
      def setReadOnly(a: Boolean): F[Unit]
      def setSavepoint: F[Savepoint]
      def setSavepoint(a: String): F[Savepoint]
      def setSchema(a: String): F[Unit]
      def setTransactionIsolation(a: Int): F[Unit]
      def setTypeMap(a: Map[String, Class[_]]): F[Unit]
      def unwrap[T](a: Class[T]): F[T]

    }

    // Common operations for all algebras.
    final case class Raw[A](f: Connection => A) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raw(f)
    }
    final case class Embed[A](e: Embedded[A]) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.embed(e)
    }
    final case class RaiseError[A](e: Throwable) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raiseError(e)
    }
    final case class HandleErrorWith[A](fa: ConnectionIO[A], f: Throwable => ConnectionIO[A]) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.handleErrorWith(fa)(f)
    }
    case object Monotonic extends ConnectionOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.monotonic
    }
    case object Realtime extends ConnectionOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.realTime
    }
    case class Suspend[A](hint: Sync.Type, thunk: () => A) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.suspend(hint)(thunk())
    }
    case class ForceR[A, B](fa: ConnectionIO[A], fb: ConnectionIO[B]) extends ConnectionOp[B] {
      def visit[F[_]](v: Visitor[F]) = v.forceR(fa)(fb)
    }
    case class Uncancelable[A](body: Poll[ConnectionIO] => ConnectionIO[A]) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.uncancelable(body)
    }
    case class Poll1[A](poll: Any, fa: ConnectionIO[A]) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.poll(poll, fa)
    }
    case object Canceled extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.canceled
    }
    case class OnCancel[A](fa: ConnectionIO[A], fin: ConnectionIO[Unit]) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.onCancel(fa, fin)
    }
    case object Cede extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.cede
    }
    case class Ref1[A](a: A) extends ConnectionOp[CERef[ConnectionIO, A]] {
      def visit[F[_]](v: Visitor[F]) = v.ref(a)
    }
    case class Deferred1[A]() extends ConnectionOp[Deferred[ConnectionIO, A]] {
      def visit[F[_]](v: Visitor[F]) = v.deferred
    }
    case class Sleep(time: FiniteDuration) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.sleep(time)
    }
    case class EvalOn[A](fa: ConnectionIO[A], ec: ExecutionContext) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.evalOn(fa, ec)
    }
    case object ExecutionContext1 extends ConnectionOp[ExecutionContext] {
      def visit[F[_]](v: Visitor[F]) = v.executionContext
    }
    case class Async1[A](k: (Either[Throwable, A] => Unit) => ConnectionIO[Option[ConnectionIO[Unit]]]) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.async(k)
    }
    case class LiftIO1[A](ioa: IO[A]) extends ConnectionOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.liftIO(ioa)
    }

    // Connection-specific operations.
    final case class  Abort(a: Executor) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.abort(a)
    }
    final case object ClearWarnings extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.clearWarnings
    }
    final case object Close extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.close
    }
    final case object Commit extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.commit
    }
    final case class  CreateArrayOf(a: String, b: Array[AnyRef]) extends ConnectionOp[SqlArray] {
      def visit[F[_]](v: Visitor[F]) = v.createArrayOf(a, b)
    }
    final case object CreateBlob extends ConnectionOp[Blob] {
      def visit[F[_]](v: Visitor[F]) = v.createBlob
    }
    final case object CreateClob extends ConnectionOp[Clob] {
      def visit[F[_]](v: Visitor[F]) = v.createClob
    }
    final case object CreateNClob extends ConnectionOp[NClob] {
      def visit[F[_]](v: Visitor[F]) = v.createNClob
    }
    final case object CreateSQLXML extends ConnectionOp[SQLXML] {
      def visit[F[_]](v: Visitor[F]) = v.createSQLXML
    }
    final case object CreateStatement extends ConnectionOp[Statement] {
      def visit[F[_]](v: Visitor[F]) = v.createStatement
    }
    final case class  CreateStatement1(a: Int, b: Int) extends ConnectionOp[Statement] {
      def visit[F[_]](v: Visitor[F]) = v.createStatement(a, b)
    }
    final case class  CreateStatement2(a: Int, b: Int, c: Int) extends ConnectionOp[Statement] {
      def visit[F[_]](v: Visitor[F]) = v.createStatement(a, b, c)
    }
    final case class  CreateStruct(a: String, b: Array[AnyRef]) extends ConnectionOp[Struct] {
      def visit[F[_]](v: Visitor[F]) = v.createStruct(a, b)
    }
    final case object GetAutoCommit extends ConnectionOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.getAutoCommit
    }
    final case object GetCatalog extends ConnectionOp[String] {
      def visit[F[_]](v: Visitor[F]) = v.getCatalog
    }
    final case object GetClientInfo extends ConnectionOp[Properties] {
      def visit[F[_]](v: Visitor[F]) = v.getClientInfo
    }
    final case class  GetClientInfo1(a: String) extends ConnectionOp[String] {
      def visit[F[_]](v: Visitor[F]) = v.getClientInfo(a)
    }
    final case object GetHoldability extends ConnectionOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getHoldability
    }
    final case object GetMetaData extends ConnectionOp[DatabaseMetaData] {
      def visit[F[_]](v: Visitor[F]) = v.getMetaData
    }
    final case object GetNetworkTimeout extends ConnectionOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getNetworkTimeout
    }
    final case object GetSchema extends ConnectionOp[String] {
      def visit[F[_]](v: Visitor[F]) = v.getSchema
    }
    final case object GetTransactionIsolation extends ConnectionOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getTransactionIsolation
    }
    final case object GetTypeMap extends ConnectionOp[Map[String, Class[_]]] {
      def visit[F[_]](v: Visitor[F]) = v.getTypeMap
    }
    final case object GetWarnings extends ConnectionOp[SQLWarning] {
      def visit[F[_]](v: Visitor[F]) = v.getWarnings
    }
    final case object IsClosed extends ConnectionOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isClosed
    }
    final case object IsReadOnly extends ConnectionOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isReadOnly
    }
    final case class  IsValid(a: Int) extends ConnectionOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isValid(a)
    }
    final case class  IsWrapperFor(a: Class[_]) extends ConnectionOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isWrapperFor(a)
    }
    final case class  NativeSQL(a: String) extends ConnectionOp[String] {
      def visit[F[_]](v: Visitor[F]) = v.nativeSQL(a)
    }
    final case class  PrepareCall(a: String) extends ConnectionOp[CallableStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareCall(a)
    }
    final case class  PrepareCall1(a: String, b: Int, c: Int) extends ConnectionOp[CallableStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareCall(a, b, c)
    }
    final case class  PrepareCall2(a: String, b: Int, c: Int, d: Int) extends ConnectionOp[CallableStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareCall(a, b, c, d)
    }
    final case class  PrepareStatement(a: String) extends ConnectionOp[PreparedStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareStatement(a)
    }
    final case class  PrepareStatement1(a: String, b: Array[Int]) extends ConnectionOp[PreparedStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareStatement(a, b)
    }
    final case class  PrepareStatement2(a: String, b: Array[String]) extends ConnectionOp[PreparedStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareStatement(a, b)
    }
    final case class  PrepareStatement3(a: String, b: Int) extends ConnectionOp[PreparedStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareStatement(a, b)
    }
    final case class  PrepareStatement4(a: String, b: Int, c: Int) extends ConnectionOp[PreparedStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareStatement(a, b, c)
    }
    final case class  PrepareStatement5(a: String, b: Int, c: Int, d: Int) extends ConnectionOp[PreparedStatement] {
      def visit[F[_]](v: Visitor[F]) = v.prepareStatement(a, b, c, d)
    }
    final case class  ReleaseSavepoint(a: Savepoint) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.releaseSavepoint(a)
    }
    final case object Rollback extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.rollback
    }
    final case class  Rollback1(a: Savepoint) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.rollback(a)
    }
    final case class  SetAutoCommit(a: Boolean) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setAutoCommit(a)
    }
    final case class  SetCatalog(a: String) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setCatalog(a)
    }
    final case class  SetClientInfo(a: Properties) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setClientInfo(a)
    }
    final case class  SetClientInfo1(a: String, b: String) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setClientInfo(a, b)
    }
    final case class  SetHoldability(a: Int) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setHoldability(a)
    }
    final case class  SetNetworkTimeout(a: Executor, b: Int) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setNetworkTimeout(a, b)
    }
    final case class  SetReadOnly(a: Boolean) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setReadOnly(a)
    }
    final case object SetSavepoint extends ConnectionOp[Savepoint] {
      def visit[F[_]](v: Visitor[F]) = v.setSavepoint
    }
    final case class  SetSavepoint1(a: String) extends ConnectionOp[Savepoint] {
      def visit[F[_]](v: Visitor[F]) = v.setSavepoint(a)
    }
    final case class  SetSchema(a: String) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setSchema(a)
    }
    final case class  SetTransactionIsolation(a: Int) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setTransactionIsolation(a)
    }
    final case class  SetTypeMap(a: Map[String, Class[_]]) extends ConnectionOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.setTypeMap(a)
    }
    final case class  Unwrap[T](a: Class[T]) extends ConnectionOp[T] {
      def visit[F[_]](v: Visitor[F]) = v.unwrap(a)
    }

  }
  import ConnectionOp._

  // Smart constructors for operations common to all algebras.
  val unit: ConnectionIO[Unit] = FF.pure[ConnectionOp, Unit](())
  def pure[A](a: A): ConnectionIO[A] = FF.pure[ConnectionOp, A](a)
  def raw[A](f: Connection => A): ConnectionIO[A] = FF.liftF(Raw(f))
  def embed[F[_], J, A](j: J, fa: FF[F, A])(implicit ev: Embeddable[F, J]): FF[ConnectionOp, A] = FF.liftF(Embed(ev.embed(j, fa)))
  def raiseError[A](err: Throwable): ConnectionIO[A] = FF.liftF[ConnectionOp, A](RaiseError(err))
  def handleErrorWith[A](fa: ConnectionIO[A])(f: Throwable => ConnectionIO[A]): ConnectionIO[A] = FF.liftF[ConnectionOp, A](HandleErrorWith(fa, f))
  val monotonic = FF.liftF[ConnectionOp, FiniteDuration](Monotonic)
  val realtime = FF.liftF[ConnectionOp, FiniteDuration](Realtime)
  def delay[A](thunk: => A) = FF.liftF[ConnectionOp, A](Suspend(Sync.Type.Delay, () => thunk))
  def suspend[A](hint: Sync.Type)(thunk: => A) = FF.liftF[ConnectionOp, A](Suspend(hint, () => thunk))
  def forceR[A, B](fa: ConnectionIO[A])(fb: ConnectionIO[B]) = FF.liftF[ConnectionOp, B](ForceR(fa, fb))
  def uncancelable[A](body: Poll[ConnectionIO] => ConnectionIO[A]) = FF.liftF[ConnectionOp, A](Uncancelable(body))
  def capturePoll[M[_]](mpoll: Poll[M]) = new Poll[ConnectionIO] {
    def apply[A](fa: ConnectionIO[A]) = FF.liftF[ConnectionOp, A](Poll1(mpoll, fa))
  }
  val canceled = FF.liftF[ConnectionOp, Unit](Canceled)
  def onCancel[A](fa: ConnectionIO[A], fin: ConnectionIO[Unit]) = FF.liftF[ConnectionOp, A](OnCancel(fa, fin))
  val cede = FF.liftF[ConnectionOp, Unit](Cede)
  def ref[A](a: A) = FF.liftF[ConnectionOp, CERef[ConnectionIO, A]](Ref1(a))
  def deferred[A] = FF.liftF[ConnectionOp, Deferred[ConnectionIO, A]](Deferred1())
  def sleep(time: FiniteDuration) = FF.liftF[ConnectionOp, Unit](Sleep(time))
  def evalOn[A](fa: ConnectionIO[A], ec: ExecutionContext) = FF.liftF[ConnectionOp, A](EvalOn(fa, ec))
  val executionContext = FF.liftF[ConnectionOp, ExecutionContext](ExecutionContext1)
  def async[A](k: (Either[Throwable, A] => Unit) => ConnectionIO[Option[ConnectionIO[Unit]]]) = FF.liftF[ConnectionOp, A](Async1(k))

  def liftIO[A](ioa: IO[A]) = FF.liftF[ConnectionOp, A](LiftIO1(ioa))

  // Smart constructors for Connection-specific operations.
  def abort(a: Executor): ConnectionIO[Unit] = FF.liftF(Abort(a))
  val clearWarnings: ConnectionIO[Unit] = FF.liftF(ClearWarnings)
  val close: ConnectionIO[Unit] = FF.liftF(Close)
  val commit: ConnectionIO[Unit] = FF.liftF(Commit)
  def createArrayOf(a: String, b: Array[AnyRef]): ConnectionIO[SqlArray] = FF.liftF(CreateArrayOf(a, b))
  val createBlob: ConnectionIO[Blob] = FF.liftF(CreateBlob)
  val createClob: ConnectionIO[Clob] = FF.liftF(CreateClob)
  val createNClob: ConnectionIO[NClob] = FF.liftF(CreateNClob)
  val createSQLXML: ConnectionIO[SQLXML] = FF.liftF(CreateSQLXML)
  val createStatement: ConnectionIO[Statement] = FF.liftF(CreateStatement)
  def createStatement(a: Int, b: Int): ConnectionIO[Statement] = FF.liftF(CreateStatement1(a, b))
  def createStatement(a: Int, b: Int, c: Int): ConnectionIO[Statement] = FF.liftF(CreateStatement2(a, b, c))
  def createStruct(a: String, b: Array[AnyRef]): ConnectionIO[Struct] = FF.liftF(CreateStruct(a, b))
  val getAutoCommit: ConnectionIO[Boolean] = FF.liftF(GetAutoCommit)
  val getCatalog: ConnectionIO[String] = FF.liftF(GetCatalog)
  val getClientInfo: ConnectionIO[Properties] = FF.liftF(GetClientInfo)
  def getClientInfo(a: String): ConnectionIO[String] = FF.liftF(GetClientInfo1(a))
  val getHoldability: ConnectionIO[Int] = FF.liftF(GetHoldability)
  val getMetaData: ConnectionIO[DatabaseMetaData] = FF.liftF(GetMetaData)
  val getNetworkTimeout: ConnectionIO[Int] = FF.liftF(GetNetworkTimeout)
  val getSchema: ConnectionIO[String] = FF.liftF(GetSchema)
  val getTransactionIsolation: ConnectionIO[Int] = FF.liftF(GetTransactionIsolation)
  val getTypeMap: ConnectionIO[Map[String, Class[_]]] = FF.liftF(GetTypeMap)
  val getWarnings: ConnectionIO[SQLWarning] = FF.liftF(GetWarnings)
  val isClosed: ConnectionIO[Boolean] = FF.liftF(IsClosed)
  val isReadOnly: ConnectionIO[Boolean] = FF.liftF(IsReadOnly)
  def isValid(a: Int): ConnectionIO[Boolean] = FF.liftF(IsValid(a))
  def isWrapperFor(a: Class[_]): ConnectionIO[Boolean] = FF.liftF(IsWrapperFor(a))
  def nativeSQL(a: String): ConnectionIO[String] = FF.liftF(NativeSQL(a))
  def prepareCall(a: String): ConnectionIO[CallableStatement] = FF.liftF(PrepareCall(a))
  def prepareCall(a: String, b: Int, c: Int): ConnectionIO[CallableStatement] = FF.liftF(PrepareCall1(a, b, c))
  def prepareCall(a: String, b: Int, c: Int, d: Int): ConnectionIO[CallableStatement] = FF.liftF(PrepareCall2(a, b, c, d))
  def prepareStatement(a: String): ConnectionIO[PreparedStatement] = FF.liftF(PrepareStatement(a))
  def prepareStatement(a: String, b: Array[Int]): ConnectionIO[PreparedStatement] = FF.liftF(PrepareStatement1(a, b))
  def prepareStatement(a: String, b: Array[String]): ConnectionIO[PreparedStatement] = FF.liftF(PrepareStatement2(a, b))
  def prepareStatement(a: String, b: Int): ConnectionIO[PreparedStatement] = FF.liftF(PrepareStatement3(a, b))
  def prepareStatement(a: String, b: Int, c: Int): ConnectionIO[PreparedStatement] = FF.liftF(PrepareStatement4(a, b, c))
  def prepareStatement(a: String, b: Int, c: Int, d: Int): ConnectionIO[PreparedStatement] = FF.liftF(PrepareStatement5(a, b, c, d))
  def releaseSavepoint(a: Savepoint): ConnectionIO[Unit] = FF.liftF(ReleaseSavepoint(a))
  val rollback: ConnectionIO[Unit] = FF.liftF(Rollback)
  def rollback(a: Savepoint): ConnectionIO[Unit] = FF.liftF(Rollback1(a))
  def setAutoCommit(a: Boolean): ConnectionIO[Unit] = FF.liftF(SetAutoCommit(a))
  def setCatalog(a: String): ConnectionIO[Unit] = FF.liftF(SetCatalog(a))
  def setClientInfo(a: Properties): ConnectionIO[Unit] = FF.liftF(SetClientInfo(a))
  def setClientInfo(a: String, b: String): ConnectionIO[Unit] = FF.liftF(SetClientInfo1(a, b))
  def setHoldability(a: Int): ConnectionIO[Unit] = FF.liftF(SetHoldability(a))
  def setNetworkTimeout(a: Executor, b: Int): ConnectionIO[Unit] = FF.liftF(SetNetworkTimeout(a, b))
  def setReadOnly(a: Boolean): ConnectionIO[Unit] = FF.liftF(SetReadOnly(a))
  val setSavepoint: ConnectionIO[Savepoint] = FF.liftF(SetSavepoint)
  def setSavepoint(a: String): ConnectionIO[Savepoint] = FF.liftF(SetSavepoint1(a))
  def setSchema(a: String): ConnectionIO[Unit] = FF.liftF(SetSchema(a))
  def setTransactionIsolation(a: Int): ConnectionIO[Unit] = FF.liftF(SetTransactionIsolation(a))
  def setTypeMap(a: Map[String, Class[_]]): ConnectionIO[Unit] = FF.liftF(SetTypeMap(a))
  def unwrap[T](a: Class[T]): ConnectionIO[T] = FF.liftF(Unwrap(a))

  // ConnectionIO is an Async
  implicit val AsyncConnectionIO: Async[ConnectionIO] =
    new Async[ConnectionIO] {
      val asyncM = FF.catsFreeMonadForFree[ConnectionOp]
      override def pure[A](x: A): ConnectionIO[A] = asyncM.pure(x)
      override def flatMap[A, B](fa: ConnectionIO[A])(f: A => ConnectionIO[B]): ConnectionIO[B] = asyncM.flatMap(fa)(f)
      override def tailRecM[A, B](a: A)(f: A => ConnectionIO[Either[A, B]]): ConnectionIO[B] = asyncM.tailRecM(a)(f)
      override def raiseError[A](e: Throwable): ConnectionIO[A] = module.raiseError(e)
      override def handleErrorWith[A](fa: ConnectionIO[A])(f: Throwable => ConnectionIO[A]): ConnectionIO[A] = module.handleErrorWith(fa)(f)
      override def monotonic: ConnectionIO[FiniteDuration] = module.monotonic
      override def realTime: ConnectionIO[FiniteDuration] = module.realtime
      override def suspend[A](hint: Sync.Type)(thunk: => A): ConnectionIO[A] = module.suspend(hint)(thunk)
      override def forceR[A, B](fa: ConnectionIO[A])(fb: ConnectionIO[B]): ConnectionIO[B] = module.forceR(fa)(fb)
      override def uncancelable[A](body: Poll[ConnectionIO] => ConnectionIO[A]): ConnectionIO[A] = module.uncancelable(body)
      override def canceled: ConnectionIO[Unit] = module.canceled
      override def onCancel[A](fa: ConnectionIO[A], fin: ConnectionIO[Unit]): ConnectionIO[A] = module.onCancel(fa, fin)
      override def start[A](fa: ConnectionIO[A]): ConnectionIO[Fiber[ConnectionIO, Throwable, A]] = module.raiseError(new Exception("Unimplemented"))
      override def cede: ConnectionIO[Unit] = module.cede
      override def racePair[A, B](fa: ConnectionIO[A], fb: ConnectionIO[B]): ConnectionIO[Either[(Outcome[ConnectionIO, Throwable, A], Fiber[ConnectionIO, Throwable, B]), (Fiber[ConnectionIO, Throwable, A], Outcome[ConnectionIO, Throwable, B])]] = module.raiseError(new Exception("Unimplemented"))
      override def ref[A](a: A): ConnectionIO[CERef[ConnectionIO, A]] = module.ref(a)
      override def deferred[A]: ConnectionIO[Deferred[ConnectionIO, A]] = module.deferred
      override def sleep(time: FiniteDuration): ConnectionIO[Unit] = module.sleep(time)
      override def evalOn[A](fa: ConnectionIO[A], ec: ExecutionContext): ConnectionIO[A] = module.evalOn(fa, ec)
      override def executionContext: ConnectionIO[ExecutionContext] = module.executionContext
      override def async[A](k: (Either[Throwable, A] => Unit) => ConnectionIO[Option[ConnectionIO[Unit]]]) = module.async(k)
      override def cont[A](body: Cont[ConnectionIO, A]): ConnectionIO[A] = Async.defaultCont(body)(this)
    }

    implicit val LiftIOConnectionIO: LiftIO[ConnectionIO] =
      new LiftIO[ConnectionIO] {
        def liftIO[A](ioa: IO[A]): ConnectionIO[A] = module.liftIO(ioa)
      }
  }