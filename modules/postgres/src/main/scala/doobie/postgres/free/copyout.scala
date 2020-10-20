// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.postgres.free

import cats.{ ~>, MonadError }
import cats.effect.kernel.{ MonadCancel, Poll, Sync }
import cats.free.{ Free => FF } // alias because some algebras have an op called Free
import scala.concurrent.duration.FiniteDuration
import com.github.ghik.silencer.silent

import org.postgresql.copy.{ CopyOut => PGCopyOut }

@silent("deprecated")
object copyout { module =>

  // Algebra of operations for PGCopyOut. Each accepts a visitor as an alternative to pattern-matching.
  sealed trait CopyOutOp[A] {
    def visit[F[_]](v: CopyOutOp.Visitor[F]): F[A]
  }

  // Free monad over CopyOutOp.
  type CopyOutIO[A] = FF[CopyOutOp, A]

  // Module of instances and constructors of CopyOutOp.
  object CopyOutOp {

    // Given a PGCopyOut we can embed a CopyOutIO program in any algebra that understands embedding.
    implicit val CopyOutOpEmbeddable: Embeddable[CopyOutOp, PGCopyOut] =
      new Embeddable[CopyOutOp, PGCopyOut] {
        def embed[A](j: PGCopyOut, fa: FF[CopyOutOp, A]) = Embedded.CopyOut(j, fa)
      }

    // Interface for a natural transformation CopyOutOp ~> F encoded via the visitor pattern.
    // This approach is much more efficient than pattern-matching for large algebras.
    trait Visitor[F[_]] extends (CopyOutOp ~> F) {
      final def apply[A](fa: CopyOutOp[A]): F[A] = fa.visit(this)

      // Common
      def raw[A](f: PGCopyOut => A): F[A]
      def embed[A](e: Embedded[A]): F[A]
      def raiseError[A](e: Throwable): F[A]
      def handleErrorWith[A](fa: CopyOutIO[A])(f: Throwable => CopyOutIO[A]): F[A]
      def monotonic: F[FiniteDuration]
      def realTime: F[FiniteDuration]
      def delay[A](thunk: => A): F[A]
      def suspend[A](hint: Sync.Type)(thunk: => A): F[A]
      def forceR[A, B](fa: CopyOutIO[A])(fb: CopyOutIO[B]): F[B]
      def uncancelable[A](body: Poll[CopyOutIO] => CopyOutIO[A]): F[A]
      def poll[A](poll: Any, fa: CopyOutIO[A]): F[A]
      def canceled: F[Unit]
      def onCancel[A](fa: CopyOutIO[A], fin: CopyOutIO[Unit]): F[A]

      // PGCopyOut
      def cancelCopy: F[Unit]
      def getFieldCount: F[Int]
      def getFieldFormat(a: Int): F[Int]
      def getFormat: F[Int]
      def getHandledRowCount: F[Long]
      def isActive: F[Boolean]
      def readFromCopy: F[Array[Byte]]
      def readFromCopy(a: Boolean): F[Array[Byte]]

    }

    // Common operations for all algebras.
    final case class Raw[A](f: PGCopyOut => A) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raw(f)
    }
    final case class Embed[A](e: Embedded[A]) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.embed(e)
    }
    final case class RaiseError[A](e: Throwable) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raiseError(e)
    }
    final case class HandleErrorWith[A](fa: CopyOutIO[A], f: Throwable => CopyOutIO[A]) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.handleErrorWith(fa)(f)
    }
    case object Monotonic extends CopyOutOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.monotonic
    }
    case object Realtime extends CopyOutOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.realTime
    }
    case class Suspend[A](hint: Sync.Type, thunk: () => A) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.suspend(hint)(thunk())
    }
    case class ForceR[A, B](fa: CopyOutIO[A], fb: CopyOutIO[B]) extends CopyOutOp[B] {
      def visit[F[_]](v: Visitor[F]) = v.forceR(fa)(fb)
    }
    case class Uncancelable[A](body: Poll[CopyOutIO] => CopyOutIO[A]) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.uncancelable(body)
    }
    case class Poll1[A](poll: Any, fa: CopyOutIO[A]) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.poll(poll, fa)
    }
    case object Canceled extends CopyOutOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.canceled
    }
    case class OnCancel[A](fa: CopyOutIO[A], fin: CopyOutIO[Unit]) extends CopyOutOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.onCancel(fa, fin)
    }

    // PGCopyOut-specific operations.
    final case object CancelCopy extends CopyOutOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.cancelCopy
    }
    final case object GetFieldCount extends CopyOutOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getFieldCount
    }
    final case class  GetFieldFormat(a: Int) extends CopyOutOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getFieldFormat(a)
    }
    final case object GetFormat extends CopyOutOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.getFormat
    }
    final case object GetHandledRowCount extends CopyOutOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.getHandledRowCount
    }
    final case object IsActive extends CopyOutOp[Boolean] {
      def visit[F[_]](v: Visitor[F]) = v.isActive
    }
    final case object ReadFromCopy extends CopyOutOp[Array[Byte]] {
      def visit[F[_]](v: Visitor[F]) = v.readFromCopy
    }
    final case class  ReadFromCopy1(a: Boolean) extends CopyOutOp[Array[Byte]] {
      def visit[F[_]](v: Visitor[F]) = v.readFromCopy(a)
    }

  }
  import CopyOutOp._

  // Smart constructors for operations common to all algebras.
  val unit: CopyOutIO[Unit] = FF.pure[CopyOutOp, Unit](())
  def pure[A](a: A): CopyOutIO[A] = FF.pure[CopyOutOp, A](a)
  def raw[A](f: PGCopyOut => A): CopyOutIO[A] = FF.liftF(Raw(f))
  def embed[F[_], J, A](j: J, fa: FF[F, A])(implicit ev: Embeddable[F, J]): FF[CopyOutOp, A] = FF.liftF(Embed(ev.embed(j, fa)))
  def raiseError[A](err: Throwable): CopyOutIO[A] = FF.liftF[CopyOutOp, A](RaiseError(err))
  def handleErrorWith[A](fa: CopyOutIO[A])(f: Throwable => CopyOutIO[A]): CopyOutIO[A] = FF.liftF[CopyOutOp, A](HandleErrorWith(fa, f))
  val monotonic = FF.liftF[CopyOutOp, FiniteDuration](Monotonic)
  val realtime = FF.liftF[CopyOutOp, FiniteDuration](Realtime)
  def delay[A](thunk: => A) = FF.liftF[CopyOutOp, A](Suspend(Sync.Type.Delay, () => thunk))
  def suspend[A](hint: Sync.Type)(thunk: => A) = FF.liftF[CopyOutOp, A](Suspend(hint, () => thunk))
  def forceR[A, B](fa: CopyOutIO[A])(fb: CopyOutIO[B]) = FF.liftF[CopyOutOp, B](ForceR(fa, fb))
  def uncancelable[A](body: Poll[CopyOutIO] => CopyOutIO[A]) = FF.liftF[CopyOutOp, A](Uncancelable(body))
  def capturePoll[M[_]](mpoll: Poll[M]) = new Poll[CopyOutIO] {
    def apply[A](fa: CopyOutIO[A]) = FF.liftF[CopyOutOp, A](Poll1(mpoll, fa))
  }
  val canceled = FF.liftF[CopyOutOp, Unit](Canceled)
  def onCancel[A](fa: CopyOutIO[A], fin: CopyOutIO[Unit]) = FF.liftF[CopyOutOp, A](OnCancel(fa, fin))

  // Smart constructors for CopyOut-specific operations.
  val cancelCopy: CopyOutIO[Unit] = FF.liftF(CancelCopy)
  val getFieldCount: CopyOutIO[Int] = FF.liftF(GetFieldCount)
  def getFieldFormat(a: Int): CopyOutIO[Int] = FF.liftF(GetFieldFormat(a))
  val getFormat: CopyOutIO[Int] = FF.liftF(GetFormat)
  val getHandledRowCount: CopyOutIO[Long] = FF.liftF(GetHandledRowCount)
  val isActive: CopyOutIO[Boolean] = FF.liftF(IsActive)
  val readFromCopy: CopyOutIO[Array[Byte]] = FF.liftF(ReadFromCopy)
  def readFromCopy(a: Boolean): CopyOutIO[Array[Byte]] = FF.liftF(ReadFromCopy1(a))

  // Typeclass instances for CopyOutIO
  trait MonadErrorCopyOutIO extends MonadError[CopyOutIO, Throwable] {
    val monad = FF.catsFreeMonadForFree[CopyOutOp]
    override def pure[A](x: A): CopyOutIO[A] = monad.pure(x)
    override def flatMap[A, B](fa: CopyOutIO[A])(f: A => CopyOutIO[B]): CopyOutIO[B] = monad.flatMap(fa)(f)
    override def tailRecM[A, B](a: A)(f: A => CopyOutIO[Either[A, B]]): CopyOutIO[B] = monad.tailRecM(a)(f)
    override def raiseError[A](e: Throwable): CopyOutIO[A] = module.raiseError(e)
    override def handleErrorWith[A](fa: CopyOutIO[A])(f: Throwable => CopyOutIO[A]): CopyOutIO[A] = module.handleErrorWith(fa)(f)
  }

  // Typeclass instances for CopyOutIO
  implicit val SyncMonadCancelCopyOutIO: Sync[CopyOutIO] with MonadCancel[CopyOutIO, Throwable] =
    new Sync[CopyOutIO] with MonadCancel[CopyOutIO, Throwable] {
      val monad = FF.catsFreeMonadForFree[CopyOutOp]
      override def pure[A](x: A): CopyOutIO[A] = monad.pure(x)
      override def flatMap[A, B](fa: CopyOutIO[A])(f: A => CopyOutIO[B]): CopyOutIO[B] = monad.flatMap(fa)(f)
      override def tailRecM[A, B](a: A)(f: A => CopyOutIO[Either[A, B]]): CopyOutIO[B] = monad.tailRecM(a)(f)
      override def raiseError[A](e: Throwable): CopyOutIO[A] = module.raiseError(e)
      override def handleErrorWith[A](fa: CopyOutIO[A])(f: Throwable => CopyOutIO[A]): CopyOutIO[A] = module.handleErrorWith(fa)(f)
      override def monotonic: CopyOutIO[FiniteDuration] = module.monotonic
      override def realTime: CopyOutIO[FiniteDuration] = module.realtime
      override def suspend[A](hint: Sync.Type)(thunk: => A): CopyOutIO[A] = module.suspend(hint)(thunk)
      override def forceR[A, B](fa: CopyOutIO[A])(fb: CopyOutIO[B]): CopyOutIO[B] = module.forceR(fa)(fb)
      override def uncancelable[A](body: Poll[CopyOutIO] => CopyOutIO[A]): CopyOutIO[A] = module.uncancelable(body)
      override def canceled: CopyOutIO[Unit] = module.canceled
      override def onCancel[A](fa: CopyOutIO[A], fin: CopyOutIO[Unit]): CopyOutIO[A] = module.onCancel(fa, fin)
    }
}

