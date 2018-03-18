// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import cats.Semigroupal
import cats.{ Invariant => InvariantFunctor }

import doobie.enum.Nullability._
import doobie.free._
import doobie.util.kernel.Kernel
import doobie.util.meta.Meta

import java.sql.{ PreparedStatement, ResultSet }

import shapeless.{ HList, HNil, ::, Generic, Lazy, <:!< }
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound



/**
 * Module defining a typeclass for composite database types (those that can map to multiple columns).
 */
object composite {

  @implicitNotFound("""Could not find or construct Composite[${A}].

  If ${A} is a simple type (or option thereof) that maps to a single column, you're
  probably missing a Meta instance. If ${A} is a product type (typically a case class,
  tuple, or HList) then probably one of its component types is missing a Composite instance. You can
  usually diagnose this by evaluating Composite[Foo] for each element Foo of the product type in
  question. See the FAQ in the Book of Doobie for more hints.
""")
  trait Composite[A] { c =>

    private[composite] val kernel: Kernel[A]
    final lazy val length: Int = kernel.width
    val meta: List[(Meta[_], NullabilityKnown)]

    /** Flatten the composite into its untyped constituents. This is only useful for logging. */
    val toList: A => List[Any]

    val set: (Int, A) => FPS.PreparedStatementIO[Unit] =
      (n, a) => FPS.raw(ps => kernel.set(ps, n, kernel.ai(a)))

    val update: (Int, A) => FRS.ResultSetIO[Unit] =
      (n, a) => FRS.raw(rs => kernel.update(rs, n, kernel.ai(a)))

    val get: Int => FRS.ResultSetIO[A] =
      n => FRS.raw(rs => kernel.ia(kernel.get(rs, n)))

    val unsafeGet: (ResultSet, Int) => A =
      (rs, n) => kernel.ia(kernel.get(rs, n))

    val unsafeSet: (PreparedStatement, Int, A) => Unit =
      (ps, n, a) => kernel.set(ps, n, kernel.ai(a))

    def imap[B](f: A => B)(g: B => A): Composite[B] =
      new Composite[B] {
        val kernel = c.kernel.imap(f)(g)
        val meta   = c.meta
        val toList = (b: B) => c.toList(g(b))
      }

    /** Product of two Composites. */
    def zip[B](cb: Composite[B]): Composite[(A, B)] =
      new Composite[(A, B)] {
        val kernel = c.kernel.zip(cb.kernel)
        val meta   = c.meta ++ cb.meta
        val toList = (p: (A, B)) => c.toList(p._1) ++ cb.toList(p._2)
      }

  }

  object Composite extends LowerPriorityComposite {

    def apply[A](implicit A: Composite[A]): Composite[A] = A

    final case class ForGeneric[F]() {
      def derive[G]()(implicit gen: Generic.Aux[F, G], G: Lazy[Composite[G]]): Composite[F] =
        new Composite[F] {
          val kernel = G.value.kernel.imap(gen.from)(gen.to)
          val meta   = G.value.meta
          val toList = (f: F) => G.value.toList(gen.to(f))
        }
    }

    implicit val compositeInvariantFunctor: InvariantFunctor[Composite] =
      new InvariantFunctor[Composite] {
        def imap[A, B](ma: Composite[A])(f: A => B)(g: B => A): Composite[B] =
          ma.imap(f)(g)
      }

    implicit val compositeSemigroupal: Semigroupal[Composite] =
      new Semigroupal[Composite] {
        def product[A, B](a: Composite[A], b: Composite[B]): Composite[(A, B)] =
          a.zip(b)
      }

    implicit val unitComposite: Composite[Unit] =
      emptyProduct.imap(_ => ())(_ => HNil)

    implicit def fromMeta[A](implicit A: Meta[A]): Composite[A] =
      new Composite[A] {
        val kernel = new Kernel[A] {
          type I      = A
          val ia      = (i: I) => i
          val ai      = (a: A) => a
          val get     = A.unsafeGetNonNullable _
          val set     = A.unsafeSetNonNullable _
          val setNull = A.unsafeSetNull _
          val update  = A.unsafeUpdateNonNullable _
          val width   = A.kernel.width
        }
        val meta   = List((A, NoNulls))
        val toList = (a: A) => List(a)
      }

    implicit def fromMetaOption[A](implicit A: Meta[A]): Composite[Option[A]] =
      new Composite[Option[A]] {
        val kernel = new Kernel[Option[A]] {
          type I      = Option[A]
          val ia      = (i: I) => i
          val ai      = (a: I) => a
          val get     = A.unsafeGetNullable _
          val set     = A.unsafeSetNullable _
          val setNull = A.unsafeSetNull _
          val update  = A.unsafeUpdateNullable _
          val width   = A.kernel.width
        }
        val meta   = List((A, Nullable))
        val toList = (a: Option[A]) => List(a)
      }

    // Composite for shapeless record types
    implicit def recordComposite[K <: Symbol, H, T <: HList](
      implicit H: Lazy[Composite[H]],
               T: Lazy[Composite[T]]
    ): Composite[FieldType[K, H] :: T] =
      new Composite[FieldType[K, H] :: T] {
        val kernel = Kernel.record(H.value.kernel, T.value.kernel): Kernel[FieldType[K,H] :: T] // ascription necessary in 2.11 for some reason
        val meta   = H.value.meta ++ T.value.meta
        val toList = (l: FieldType[K, H] :: T) => H.value.toList(l.head) ++ T.value.toList(l.tail)
      }

  }

  // N.B. we're separating this out in order to make the atom ~> composite derivation higher
  // priority than the product ~> composite derivation. So this means if we have an product mapped
  // to a single column, we will get only the atomic mapping, not the multi-column one.
  trait LowerPriorityComposite extends EvenLower {

    implicit def product[H, T <: HList](
      implicit H: Lazy[Composite[H]],
               T: Lazy[Composite[T]]
    ): Composite[H :: T] =
      new Composite[H :: T] {
        val kernel = Kernel.hcons(H.value.kernel, T.value.kernel)
        val meta   = H.value.meta ++ T.value.meta
        val toList = (l: H :: T) => H.value.toList(l.head) ++ T.value.toList(l.tail)
      }

    implicit def emptyProduct: Composite[HNil] =
      new Composite[HNil] {
        val kernel = Kernel.hnil
        val meta   = Nil
        val toList = (_: HNil) => Nil
      }

    implicit def generic[F, G](implicit gen: Generic.Aux[F, G], G: Lazy[Composite[G]]): Composite[F] =
      Composite.ForGeneric[F].derive()

  }

  trait EvenLower {

    implicit val ohnil: Composite[Option[HNil]] =
      new Composite[Option[HNil]] {
        val kernel = Kernel.ohnil
        val meta   = Nil
        val toList = (_: Option[HNil]) => Nil
      }

    implicit def ohcons1[H, T <: HList](
      implicit H: Lazy[Composite[Option[H]]],
               T: Lazy[Composite[Option[T]]],
               n: H <:!< Option[α] forSome { type α }
    ): Composite[Option[H :: T]] =
      new Composite[Option[H :: T]] {
        val kernel = Kernel.ohcons1(H.value.kernel, T.value.kernel)
        val meta   = H.value.meta ++ (T.value.meta, n)._1 // just to fix the unused implicit warning for `n`
        val toList = (o: Option[H :: T]) => o match {
          case Some(h :: t) => H.value.toList(Some(h)) ++ T.value.toList(Some(t))
          case None         => H.value.toList(None)    ++ T.value.toList(None)
        }
      }

    implicit def ohcons2[H, T <: HList](
      implicit H: Lazy[Composite[Option[H]]],
               T: Lazy[Composite[Option[T]]]
    ): Composite[Option[Option[H] :: T]] =
      new Composite[Option[Option[H] :: T]] {
        val kernel = Kernel.ohcons2(H.value.kernel, T.value.kernel)
        val meta   = H.value.meta ++ T.value.meta
        val toList = (o: Option[Option[H] :: T]) => o match {
          case Some(h :: t) => H.value.toList(h) ++ T.value.toList(Some(t))
          case None         => H.value.toList(None) ++ T.value.toList(None)
        }
      }

    implicit def ogeneric[A, Repr <: HList](
      implicit G: Generic.Aux[A, Repr],
               B: Lazy[Composite[Option[Repr]]]
    ): Composite[Option[A]] =
      new Composite[Option[A]] {
        val kernel = B.value.kernel.imap(_.map(G.from))(_.map(G.to))
        val meta   = B.value.meta
        val toList = (oa: Option[A]) => B.value.toList(oa.map(G.to))
      }
  }

}
