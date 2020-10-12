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

import org.postgresql.largeobject.LargeObject
import org.postgresql.largeobject.LargeObjectManager

@silent("deprecated")
object largeobjectmanager { module =>

  // Algebra of operations for LargeObjectManager. Each accepts a visitor as an alternative to pattern-matching.
  sealed trait LargeObjectManagerOp[A] {
    def visit[F[_]](v: LargeObjectManagerOp.Visitor[F]): F[A]
  }

  // Free monad over LargeObjectManagerOp.
  type LargeObjectManagerIO[A] = FF[LargeObjectManagerOp, A]

  // Module of instances and constructors of LargeObjectManagerOp.
  object LargeObjectManagerOp {

    // Given a LargeObjectManager we can embed a LargeObjectManagerIO program in any algebra that understands embedding.
    implicit val LargeObjectManagerOpEmbeddable: Embeddable[LargeObjectManagerOp, LargeObjectManager] =
      new Embeddable[LargeObjectManagerOp, LargeObjectManager] {
        def embed[A](j: LargeObjectManager, fa: FF[LargeObjectManagerOp, A]) = Embedded.LargeObjectManager(j, fa)
      }

    // Interface for a natural transformation LargeObjectManagerOp ~> F encoded via the visitor pattern.
    // This approach is much more efficient than pattern-matching for large algebras.
    trait Visitor[F[_]] extends (LargeObjectManagerOp ~> F) {
      final def apply[A](fa: LargeObjectManagerOp[A]): F[A] = fa.visit(this)

      // Common
      def raw[A](f: LargeObjectManager => A): F[A]
      def embed[A](e: Embedded[A]): F[A]
      def raiseError[A](e: Throwable): F[A]
      def handleErrorWith[A](fa: LargeObjectManagerIO[A])(f: Throwable => LargeObjectManagerIO[A]): F[A]
      def monotonic: F[FiniteDuration]
      def realTime: F[FiniteDuration]
      def delay[A](thunk: => A): F[A]
      def suspend[A](hint: Sync.Type)(thunk: => A): F[A]
      def forceR[A, B](fa: LargeObjectManagerIO[A])(fb: LargeObjectManagerIO[B]): F[B]
      def uncancelable[A](body: Poll[LargeObjectManagerIO] => LargeObjectManagerIO[A]): F[A]
      def poll[A](poll: Any, fa: LargeObjectManagerIO[A]): F[A]
      def canceled: F[Unit]
      def onCancel[A](fa: LargeObjectManagerIO[A], fin: LargeObjectManagerIO[Unit]): F[A]
      def cede: F[Unit]
      def ref[A](a: A): F[CERef[LargeObjectManagerIO, A]]
      def deferred[A]: F[Deferred[LargeObjectManagerIO, A]]
      def sleep(time: FiniteDuration): F[Unit]
      def evalOn[A](fa: LargeObjectManagerIO[A], ec: ExecutionContext): F[A]
      def executionContext: F[ExecutionContext]
      def async[A](k: (Either[Throwable, A] => Unit) => LargeObjectManagerIO[Option[LargeObjectManagerIO[Unit]]]): F[A]

      // LargeObjectManager
      def create: F[Int]
      def create(a: Int): F[Int]
      def createLO: F[Long]
      def createLO(a: Int): F[Long]
      def delete(a: Int): F[Unit]
      def delete(a: Long): F[Unit]
      def open(a: Int): F[LargeObject]
      def open(a: Int, b: Boolean): F[LargeObject]
      def open(a: Int, b: Int): F[LargeObject]
      def open(a: Int, b: Int, c: Boolean): F[LargeObject]
      def open(a: Long): F[LargeObject]
      def open(a: Long, b: Boolean): F[LargeObject]
      def open(a: Long, b: Int): F[LargeObject]
      def open(a: Long, b: Int, c: Boolean): F[LargeObject]
      def unlink(a: Int): F[Unit]
      def unlink(a: Long): F[Unit]

    }

    // Common operations for all algebras.
    final case class Raw[A](f: LargeObjectManager => A) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raw(f)
    }
    final case class Embed[A](e: Embedded[A]) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.embed(e)
    }
    final case class RaiseError[A](e: Throwable) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raiseError(e)
    }
    final case class HandleErrorWith[A](fa: LargeObjectManagerIO[A], f: Throwable => LargeObjectManagerIO[A]) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.handleErrorWith(fa)(f)
    }
    case object Monotonic extends LargeObjectManagerOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.monotonic
    }
    case object Realtime extends LargeObjectManagerOp[FiniteDuration] {
      def visit[F[_]](v: Visitor[F]) = v.realTime
    }
    case class Suspend[A](hint: Sync.Type, thunk: () => A) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.suspend(hint)(thunk())
    }
    case class ForceR[A, B](fa: LargeObjectManagerIO[A], fb: LargeObjectManagerIO[B]) extends LargeObjectManagerOp[B] {
      def visit[F[_]](v: Visitor[F]) = v.forceR(fa)(fb)
    }
    case class Uncancelable[A](body: Poll[LargeObjectManagerIO] => LargeObjectManagerIO[A]) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.uncancelable(body)
    }
    case class Poll1[A](poll: Any, fa: LargeObjectManagerIO[A]) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.poll(poll, fa)
    }
    case object Canceled extends LargeObjectManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.canceled
    }
    case class OnCancel[A](fa: LargeObjectManagerIO[A], fin: LargeObjectManagerIO[Unit]) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.onCancel(fa, fin)
    }
    case object Cede extends LargeObjectManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.cede
    }
    case class Ref1[A](a: A) extends LargeObjectManagerOp[CERef[LargeObjectManagerIO, A]] {
      def visit[F[_]](v: Visitor[F]) = v.ref(a)
    }
    case class Deferred1[A]() extends LargeObjectManagerOp[Deferred[LargeObjectManagerIO, A]] {
      def visit[F[_]](v: Visitor[F]) = v.deferred
    }
    case class Sleep(time: FiniteDuration) extends LargeObjectManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.sleep(time)
    }
    case class EvalOn[A](fa: LargeObjectManagerIO[A], ec: ExecutionContext) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.evalOn(fa, ec)
    }
    case object ExecutionContext1 extends LargeObjectManagerOp[ExecutionContext] {
      def visit[F[_]](v: Visitor[F]) = v.executionContext
    }
    case class Async1[A](k: (Either[Throwable, A] => Unit) => LargeObjectManagerIO[Option[LargeObjectManagerIO[Unit]]]) extends LargeObjectManagerOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.async(k)
    }

    // LargeObjectManager-specific operations.
    final case object Create extends LargeObjectManagerOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.create
    }
    final case class  Create1(a: Int) extends LargeObjectManagerOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.create(a)
    }
    final case object CreateLO extends LargeObjectManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.createLO
    }
    final case class  CreateLO1(a: Int) extends LargeObjectManagerOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.createLO(a)
    }
    final case class  Delete(a: Int) extends LargeObjectManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.delete(a)
    }
    final case class  Delete1(a: Long) extends LargeObjectManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.delete(a)
    }
    final case class  Open(a: Int) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a)
    }
    final case class  Open1(a: Int, b: Boolean) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a, b)
    }
    final case class  Open2(a: Int, b: Int) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a, b)
    }
    final case class  Open3(a: Int, b: Int, c: Boolean) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a, b, c)
    }
    final case class  Open4(a: Long) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a)
    }
    final case class  Open5(a: Long, b: Boolean) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a, b)
    }
    final case class  Open6(a: Long, b: Int) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a, b)
    }
    final case class  Open7(a: Long, b: Int, c: Boolean) extends LargeObjectManagerOp[LargeObject] {
      def visit[F[_]](v: Visitor[F]) = v.open(a, b, c)
    }
    final case class  Unlink(a: Int) extends LargeObjectManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.unlink(a)
    }
    final case class  Unlink1(a: Long) extends LargeObjectManagerOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.unlink(a)
    }

  }
  import LargeObjectManagerOp._

  // Smart constructors for operations common to all algebras.
  val unit: LargeObjectManagerIO[Unit] = FF.pure[LargeObjectManagerOp, Unit](())
  def pure[A](a: A): LargeObjectManagerIO[A] = FF.pure[LargeObjectManagerOp, A](a)
  def raw[A](f: LargeObjectManager => A): LargeObjectManagerIO[A] = FF.liftF(Raw(f))
  def embed[F[_], J, A](j: J, fa: FF[F, A])(implicit ev: Embeddable[F, J]): FF[LargeObjectManagerOp, A] = FF.liftF(Embed(ev.embed(j, fa)))
  def raiseError[A](err: Throwable): LargeObjectManagerIO[A] = FF.liftF[LargeObjectManagerOp, A](RaiseError(err))
  def handleErrorWith[A](fa: LargeObjectManagerIO[A])(f: Throwable => LargeObjectManagerIO[A]): LargeObjectManagerIO[A] = FF.liftF[LargeObjectManagerOp, A](HandleErrorWith(fa, f))
  val monotonic = FF.liftF[LargeObjectManagerOp, FiniteDuration](Monotonic)
  val realtime = FF.liftF[LargeObjectManagerOp, FiniteDuration](Realtime)
  def delay[A](thunk: => A) = FF.liftF[LargeObjectManagerOp, A](Suspend(Sync.Type.Delay, () => thunk))
  def suspend[A](hint: Sync.Type)(thunk: => A) = FF.liftF[LargeObjectManagerOp, A](Suspend(hint, () => thunk))
  def forceR[A, B](fa: LargeObjectManagerIO[A])(fb: LargeObjectManagerIO[B]) = FF.liftF[LargeObjectManagerOp, B](ForceR(fa, fb))
  def uncancelable[A](body: Poll[LargeObjectManagerIO] => LargeObjectManagerIO[A]) = FF.liftF[LargeObjectManagerOp, A](Uncancelable(body))
  def capturePoll[M[_]](mpoll: Poll[M]): Poll[LargeObjectManagerIO] = new Poll[LargeObjectManagerIO] {
    def apply[A](fa: LargeObjectManagerIO[A]) = FF.liftF[LargeObjectManagerOp, A](Poll1(mpoll, fa))
  }
  val canceled = FF.liftF[LargeObjectManagerOp, Unit](Canceled)
  def onCancel[A](fa: LargeObjectManagerIO[A], fin: LargeObjectManagerIO[Unit]) = FF.liftF[LargeObjectManagerOp, A](OnCancel(fa, fin))
  val cede = FF.liftF[LargeObjectManagerOp, Unit](Cede)
  def ref[A](a: A) = FF.liftF[LargeObjectManagerOp, CERef[LargeObjectManagerIO, A]](Ref1(a))
  def deferred[A] = FF.liftF[LargeObjectManagerOp, Deferred[LargeObjectManagerIO, A]](Deferred1())
  def sleep(time: FiniteDuration) = FF.liftF[LargeObjectManagerOp, Unit](Sleep(time))
  def evalOn[A](fa: LargeObjectManagerIO[A], ec: ExecutionContext) = FF.liftF[LargeObjectManagerOp, A](EvalOn(fa, ec))
  val executionContext = FF.liftF[LargeObjectManagerOp, ExecutionContext](ExecutionContext1)
  def async[A](k: (Either[Throwable, A] => Unit) => LargeObjectManagerIO[Option[LargeObjectManagerIO[Unit]]]) = FF.liftF[LargeObjectManagerOp, A](Async1(k))

  // Smart constructors for LargeObjectManager-specific operations.
  val create: LargeObjectManagerIO[Int] = FF.liftF(Create)
  def create(a: Int): LargeObjectManagerIO[Int] = FF.liftF(Create1(a))
  val createLO: LargeObjectManagerIO[Long] = FF.liftF(CreateLO)
  def createLO(a: Int): LargeObjectManagerIO[Long] = FF.liftF(CreateLO1(a))
  def delete(a: Int): LargeObjectManagerIO[Unit] = FF.liftF(Delete(a))
  def delete(a: Long): LargeObjectManagerIO[Unit] = FF.liftF(Delete1(a))
  def open(a: Int): LargeObjectManagerIO[LargeObject] = FF.liftF(Open(a))
  def open(a: Int, b: Boolean): LargeObjectManagerIO[LargeObject] = FF.liftF(Open1(a, b))
  def open(a: Int, b: Int): LargeObjectManagerIO[LargeObject] = FF.liftF(Open2(a, b))
  def open(a: Int, b: Int, c: Boolean): LargeObjectManagerIO[LargeObject] = FF.liftF(Open3(a, b, c))
  def open(a: Long): LargeObjectManagerIO[LargeObject] = FF.liftF(Open4(a))
  def open(a: Long, b: Boolean): LargeObjectManagerIO[LargeObject] = FF.liftF(Open5(a, b))
  def open(a: Long, b: Int): LargeObjectManagerIO[LargeObject] = FF.liftF(Open6(a, b))
  def open(a: Long, b: Int, c: Boolean): LargeObjectManagerIO[LargeObject] = FF.liftF(Open7(a, b, c))
  def unlink(a: Int): LargeObjectManagerIO[Unit] = FF.liftF(Unlink(a))
  def unlink(a: Long): LargeObjectManagerIO[Unit] = FF.liftF(Unlink1(a))

  // LargeObjectManagerIO is an Async
  implicit val AsyncLargeObjectManagerIO: Async[LargeObjectManagerIO] =
    new Async[LargeObjectManagerIO] {
      val asyncM = FF.catsFreeMonadForFree[LargeObjectManagerOp]
      override def pure[A](x: A): LargeObjectManagerIO[A] = asyncM.pure(x)
      override def flatMap[A, B](fa: LargeObjectManagerIO[A])(f: A => LargeObjectManagerIO[B]): LargeObjectManagerIO[B] = asyncM.flatMap(fa)(f)
      override def tailRecM[A, B](a: A)(f: A => LargeObjectManagerIO[Either[A, B]]): LargeObjectManagerIO[B] = asyncM.tailRecM(a)(f)
      override def raiseError[A](e: Throwable): LargeObjectManagerIO[A] = module.raiseError(e)
      override def handleErrorWith[A](fa: LargeObjectManagerIO[A])(f: Throwable => LargeObjectManagerIO[A]): LargeObjectManagerIO[A] = module.handleErrorWith(fa)(f)
      override def monotonic: LargeObjectManagerIO[FiniteDuration] = module.monotonic
      override def realTime: LargeObjectManagerIO[FiniteDuration] = module.realtime
      override def suspend[A](hint: Sync.Type)(thunk: => A): LargeObjectManagerIO[A] = module.suspend(hint)(thunk)
      override def forceR[A, B](fa: LargeObjectManagerIO[A])(fb: LargeObjectManagerIO[B]): LargeObjectManagerIO[B] = module.forceR(fa)(fb)
      override def uncancelable[A](body: Poll[LargeObjectManagerIO] => LargeObjectManagerIO[A]): LargeObjectManagerIO[A] = module.uncancelable(body)
      override def canceled: LargeObjectManagerIO[Unit] = module.canceled
      override def onCancel[A](fa: LargeObjectManagerIO[A], fin: LargeObjectManagerIO[Unit]): LargeObjectManagerIO[A] = module.onCancel(fa, fin)
      override def start[A](fa: LargeObjectManagerIO[A]): LargeObjectManagerIO[Fiber[LargeObjectManagerIO, Throwable, A]] = module.raiseError(new Exception("Unimplemented"))
      override def cede: LargeObjectManagerIO[Unit] = module.cede
      override def racePair[A, B](fa: LargeObjectManagerIO[A], fb: LargeObjectManagerIO[B]): LargeObjectManagerIO[Either[(Outcome[LargeObjectManagerIO, Throwable, A], Fiber[LargeObjectManagerIO, Throwable, B]), (Fiber[LargeObjectManagerIO, Throwable, A], Outcome[LargeObjectManagerIO, Throwable, B])]] = module.raiseError(new Exception("Unimplemented"))
      override def ref[A](a: A): LargeObjectManagerIO[CERef[LargeObjectManagerIO, A]] = module.ref(a)
      override def deferred[A]: LargeObjectManagerIO[Deferred[LargeObjectManagerIO, A]] = module.deferred
      override def sleep(time: FiniteDuration): LargeObjectManagerIO[Unit] = module.sleep(time)
      override def evalOn[A](fa: LargeObjectManagerIO[A], ec: ExecutionContext): LargeObjectManagerIO[A] = module.evalOn(fa, ec)
      override def executionContext: LargeObjectManagerIO[ExecutionContext] = module.executionContext
      override def async[A](k: (Either[Throwable, A] => Unit) => LargeObjectManagerIO[Option[LargeObjectManagerIO[Unit]]]) = module.async(k)
      override def cont[A](body: Cont[LargeObjectManagerIO, A]): LargeObjectManagerIO[A] = Async.defaultCont(body)(this)
    }

}

