// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.postgres.free

import cats.~>
import cats.effect.{ Async, Cont, Fiber, Outcome, Poll, Sync }
import cats.effect.kernel.{ Deferred, Ref => CERef }
import cats.free.{ Free => FF } // alias because some algebras have an op called Free
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import com.github.ghik.silencer.silent

import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.lang.String
import org.postgresql.copy.{ CopyDual => PGCopyDual }
import org.postgresql.copy.{ CopyIn => PGCopyIn }
import org.postgresql.copy.{ CopyManager => PGCopyManager }
import org.postgresql.copy.{ CopyOut => PGCopyOut }

@silent("deprecated")
object copymanager { module =>

  // Algebra of operations for PGCopyManager. Each accepts a visitor as an alternative to pattern-matching.
  sealed trait CopyManagerOp[A] {
    def visit[F[_]](v: CopyManagerOp.Visitor[F]): F[A]
  }

  // Free monad over CopyManagerOp.
  type CopyManagerIO[A] = FF[CopyManagerOp, A]

  // Module of instances and constructors of CopyManagerOp.
  object CopyManagerOp {

    // Given a PGCopyManager we can embed a CopyManagerIO program in any algebra that understands embedding.
    implicit val CopyManagerOpEmbeddable: Embeddable[CopyManagerOp, PGCopyManager] =
      new Embeddable[CopyManagerOp, PGCopyManager] {
        def embed[A](j: PGCopyManager, fa: FF[CopyManagerOp, A]) = Embedded.CopyManager(j, fa)
      }

    // Interface for a natural transformation CopyManagerOp ~> F encoded via the visitor pattern.
    // This approach is much more efficient than pattern-matching for large algebras.
    trait Visitor[F[_]] extends (CopyManagerOp ~> F) {
      final def apply[A](fa: CopyManagerOp[A]): F[A] = fa.visit(this)

      // Common
      def raw[A](f: PGCopyManager => A): F[A]
      def embed[A](e: Embedded[A]): F[A]
      def raiseError[A](e: Throwable): F[A]
      def handleErrorWith[A](fa: CopyManagerIO[A])(f: Throwable => CopyManagerIO[A]): F[A]
      def monotonic: F[FiniteDuration]
      def realTime: F[FiniteDuration]
      def delay[A](thunk: => A): F[A]
      def suspend[A](hint: Sync.Type)(thunk: => A): F[A]
      def forceR[A, B](fa: CopyManagerIO[A])(fb: CopyManagerIO[B]): F[B]
      def uncancelable[A](body: Poll[CopyManagerIO] => CopyManagerIO[A]): F[A]
      def poll[A](poll: Any, fa: CopyManagerIO[A]): F[A]
      def canceled: F[Unit]
      def onCancel[A](fa: CopyManagerIO[A], fin: CopyManagerIO[Unit]): F[A]
      def cede: F[Unit]
      def ref[A](a: A): F[CERef[CopyManagerIO, A]]
      def deferred[A]: F[Deferred[CopyManagerIO, A]]
      def sleep(time: FiniteDuration): F[Unit]
      def evalOn[A](fa: CopyManagerIO[A], ec: ExecutionContext): F[A]
      def executionContext: F[ExecutionContext]
      def async[A](k: (Either[Throwable, A] => Unit) => CopyManagerIO[Option[CopyManagerIO[Unit]]]): F[A]

      // PGCopyManager
      def copyDual(a: String): F[PGCopyDual]
      def copyIn(a: String): F[PGCopyIn]
      def copyIn(a: String, b: InputStream): F[Long]
      def copyIn(a: String, b: InputStream, c: Int): F[Long]
      def copyIn(a: String, b: Reader): F[Long]
      def copyIn(a: String, b: Reader, c: Int): F[Long]
      def copyOut(a: String): F[PGCopyOut]
      def copyOut(a: String, b: OutputStream): F[Long]
      def copyOut(a: String, b: Writer): F[Long]

    }

    // Common operations for all algebras.
    final case class Raw[A](f: PGCopyManager => A) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raw(f)
    }
    final case class Embed[A](e: Embedded[A]) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.embed(e)
    }
    final case class RaiseError[A](e: Throwable) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raiseError(e)
    }
    final case class HandleErrorWith[A](fa: CopyManagerIO[A], f: Throwable => CopyManagerIO[A]) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.handleErrorWith(fa)(f)
    }
    case object Monotonic extends CopyManagerOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.monotonic
    }
    case object Realtime extends CopyManagerOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.realTime
    }
    case class Suspend[A](hint: Sync.Type, thunk: () => A) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.suspend(hint)(thunk())
    }
    case class ForceR[A, B](fa: CopyManagerIO[A], fb: CopyManagerIO[B]) extends CopyManagerOp[B] {
      def visit[F[_]](v: Visitor[F]) = v.forceR(fa)(fb)
    }
    case class Uncancelable[A](body: Poll[CopyManagerIO] => CopyManagerIO[A]) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.uncancelable(body)
    }
    case class Poll1[A](poll: Any, fa: CopyManagerIO[A]) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.poll(poll, fa)
    }
    case object Canceled extends CopyManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.canceled
    }
    case class OnCancel[A](fa: CopyManagerIO[A], fin: CopyManagerIO[Unit]) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.onCancel(fa, fin)
    }
    case object Cede extends CopyManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.cede
    }
    case class Ref1[A](a: A) extends CopyManagerOp[CERef[CopyManagerIO, A]] {
      def visit[F[_]](v: Visitor[F]) = v.ref(a)
    }
    case class Deferred1[A]() extends CopyManagerOp[Deferred[CopyManagerIO, A]] {
      def visit[F[_]](v: Visitor[F]) = v.deferred
    }
    case class Sleep(time: FiniteDuration) extends CopyManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.sleep(time)
    }
    case class EvalOn[A](fa: CopyManagerIO[A], ec: ExecutionContext) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.evalOn(fa, ec)
    }
    case object ExecutionContext1 extends CopyManagerOp[ExecutionContext] {
      def visit[F[_]](v: Visitor[F]) = v.executionContext
    }
    case class Async1[A](k: (Either[Throwable, A] => Unit) => CopyManagerIO[Option[CopyManagerIO[Unit]]]) extends CopyManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.async(k)
    }

    // PGCopyManager-specific operations.
    final case class  CopyDual(a: String) extends CopyManagerOp[PGCopyDual] {
      def visit[F[_]](v: Visitor[F]) = v.copyDual(a)
    }
    final case class  CopyIn(a: String) extends CopyManagerOp[PGCopyIn] {
      def visit[F[_]](v: Visitor[F]) = v.copyIn(a)
    }
    final case class  CopyIn1(a: String, b: InputStream) extends CopyManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.copyIn(a, b)
    }
    final case class  CopyIn2(a: String, b: InputStream, c: Int) extends CopyManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.copyIn(a, b, c)
    }
    final case class  CopyIn3(a: String, b: Reader) extends CopyManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.copyIn(a, b)
    }
    final case class  CopyIn4(a: String, b: Reader, c: Int) extends CopyManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.copyIn(a, b, c)
    }
    final case class  CopyOut(a: String) extends CopyManagerOp[PGCopyOut] {
      def visit[F[_]](v: Visitor[F]) = v.copyOut(a)
    }
    final case class  CopyOut1(a: String, b: OutputStream) extends CopyManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.copyOut(a, b)
    }
    final case class  CopyOut2(a: String, b: Writer) extends CopyManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.copyOut(a, b)
    }

  }
  import CopyManagerOp._

  // Smart constructors for operations common to all algebras.
  val unit: CopyManagerIO[Unit] = FF.pure[CopyManagerOp, Unit](())
  def pure[A](a: A): CopyManagerIO[A] = FF.pure[CopyManagerOp, A](a)
  def raw[A](f: PGCopyManager => A): CopyManagerIO[A] = FF.liftF(Raw(f))
  def embed[F[_], J, A](j: J, fa: FF[F, A])(implicit ev: Embeddable[F, J]): FF[CopyManagerOp, A] = FF.liftF(Embed(ev.embed(j, fa)))
  def raiseError[A](err: Throwable): CopyManagerIO[A] = FF.liftF[CopyManagerOp, A](RaiseError(err))
  def handleErrorWith[A](fa: CopyManagerIO[A])(f: Throwable => CopyManagerIO[A]): CopyManagerIO[A] = FF.liftF[CopyManagerOp, A](HandleErrorWith(fa, f))
  val monotonic = FF.liftF[CopyManagerOp, FiniteDuration](Monotonic)
  val realtime = FF.liftF[CopyManagerOp, FiniteDuration](Realtime)
  def delay[A](thunk: => A) = FF.liftF[CopyManagerOp, A](Suspend(Sync.Type.Delay, () => thunk))
  def suspend[A](hint: Sync.Type)(thunk: => A) = FF.liftF[CopyManagerOp, A](Suspend(hint, () => thunk))
  def forceR[A, B](fa: CopyManagerIO[A])(fb: CopyManagerIO[B]) = FF.liftF[CopyManagerOp, B](ForceR(fa, fb))
  def uncancelable[A](body: Poll[CopyManagerIO] => CopyManagerIO[A]) = FF.liftF[CopyManagerOp, A](Uncancelable(body))
  def capturePoll[M[_]](mpoll: Poll[M]) = new Poll[CopyManagerIO] {
    def apply[A](fa: CopyManagerIO[A]) = FF.liftF[CopyManagerOp, A](Poll1(mpoll, fa))
  }
  val canceled = FF.liftF[CopyManagerOp, Unit](Canceled)
  def onCancel[A](fa: CopyManagerIO[A], fin: CopyManagerIO[Unit]) = FF.liftF[CopyManagerOp, A](OnCancel(fa, fin))
  val cede = FF.liftF[CopyManagerOp, Unit](Cede)
  def ref[A](a: A) = FF.liftF[CopyManagerOp, CERef[CopyManagerIO, A]](Ref1(a))
  def deferred[A] = FF.liftF[CopyManagerOp, Deferred[CopyManagerIO, A]](Deferred1())
  def sleep(time: FiniteDuration) = FF.liftF[CopyManagerOp, Unit](Sleep(time))
  def evalOn[A](fa: CopyManagerIO[A], ec: ExecutionContext) = FF.liftF[CopyManagerOp, A](EvalOn(fa, ec))
  val executionContext = FF.liftF[CopyManagerOp, ExecutionContext](ExecutionContext1)
  def async[A](k: (Either[Throwable, A] => Unit) => CopyManagerIO[Option[CopyManagerIO[Unit]]]) = FF.liftF[CopyManagerOp, A](Async1(k))

  // Smart constructors for CopyManager-specific operations.
  def copyDual(a: String): CopyManagerIO[PGCopyDual] = FF.liftF(CopyDual(a))
  def copyIn(a: String): CopyManagerIO[PGCopyIn] = FF.liftF(CopyIn(a))
  def copyIn(a: String, b: InputStream): CopyManagerIO[Long] = FF.liftF(CopyIn1(a, b))
  def copyIn(a: String, b: InputStream, c: Int): CopyManagerIO[Long] = FF.liftF(CopyIn2(a, b, c))
  def copyIn(a: String, b: Reader): CopyManagerIO[Long] = FF.liftF(CopyIn3(a, b))
  def copyIn(a: String, b: Reader, c: Int): CopyManagerIO[Long] = FF.liftF(CopyIn4(a, b, c))
  def copyOut(a: String): CopyManagerIO[PGCopyOut] = FF.liftF(CopyOut(a))
  def copyOut(a: String, b: OutputStream): CopyManagerIO[Long] = FF.liftF(CopyOut1(a, b))
  def copyOut(a: String, b: Writer): CopyManagerIO[Long] = FF.liftF(CopyOut2(a, b))

  // CopyManagerIO is an Async
  implicit val AsyncCopyManagerIO: Async[CopyManagerIO] =
    new Async[CopyManagerIO] {
      val asyncM = FF.catsFreeMonadForFree[CopyManagerOp]
      override def pure[A](x: A): CopyManagerIO[A] = asyncM.pure(x)
      override def flatMap[A, B](fa: CopyManagerIO[A])(f: A => CopyManagerIO[B]): CopyManagerIO[B] = asyncM.flatMap(fa)(f)
      override def tailRecM[A, B](a: A)(f: A => CopyManagerIO[Either[A, B]]): CopyManagerIO[B] = asyncM.tailRecM(a)(f)
      override def raiseError[A](e: Throwable): CopyManagerIO[A] = module.raiseError(e)
      override def handleErrorWith[A](fa: CopyManagerIO[A])(f: Throwable => CopyManagerIO[A]): CopyManagerIO[A] = module.handleErrorWith(fa)(f)
      override def monotonic: CopyManagerIO[FiniteDuration] = module.monotonic
      override def realTime: CopyManagerIO[FiniteDuration] = module.realtime
      override def suspend[A](hint: Sync.Type)(thunk: => A): CopyManagerIO[A] = module.suspend(hint)(thunk)
      override def forceR[A, B](fa: CopyManagerIO[A])(fb: CopyManagerIO[B]): CopyManagerIO[B] = module.forceR(fa)(fb)
      override def uncancelable[A](body: Poll[CopyManagerIO] => CopyManagerIO[A]): CopyManagerIO[A] = module.uncancelable(body)
      override def canceled: CopyManagerIO[Unit] = module.canceled
      override def onCancel[A](fa: CopyManagerIO[A], fin: CopyManagerIO[Unit]): CopyManagerIO[A] = module.onCancel(fa, fin)
      override def start[A](fa: CopyManagerIO[A]): CopyManagerIO[Fiber[CopyManagerIO, Throwable, A]] = module.raiseError(new Exception("Unimplemented"))
      override def cede: CopyManagerIO[Unit] = module.cede
      override def racePair[A, B](fa: CopyManagerIO[A], fb: CopyManagerIO[B]): CopyManagerIO[Either[(Outcome[CopyManagerIO, Throwable, A], Fiber[CopyManagerIO, Throwable, B]), (Fiber[CopyManagerIO, Throwable, A], Outcome[CopyManagerIO, Throwable, B])]] = module.raiseError(new Exception("Unimplemented"))
      override def ref[A](a: A): CopyManagerIO[CERef[CopyManagerIO, A]] = module.ref(a)
      override def deferred[A]: CopyManagerIO[Deferred[CopyManagerIO, A]] = module.deferred
      override def sleep(time: FiniteDuration): CopyManagerIO[Unit] = module.sleep(time)
      override def evalOn[A](fa: CopyManagerIO[A], ec: ExecutionContext): CopyManagerIO[A] = module.evalOn(fa, ec)
      override def executionContext: CopyManagerIO[ExecutionContext] = module.executionContext
      override def async[A](k: (Either[Throwable, A] => Unit) => CopyManagerIO[Option[CopyManagerIO[Unit]]]) = module.async(k)
      override def cont[A](body: Cont[CopyManagerIO, A]): CopyManagerIO[A] = Async.defaultCont(body)(this)
    }

}

