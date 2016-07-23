package doobie.util


import doobie.free.connection.ConnectionIO
import doobie.free.preparedstatement.PreparedStatementIO
import doobie.hi.{ connection => HC }
import doobie.hi.{ preparedstatement => HPS }
import doobie.util.analysis.Analysis
import doobie.util.composite.Composite

#+scalaz
import scalaz.{ Contravariant, Foldable }
import scalaz.std.list._
import scalaz.stream.Process
import scalaz.syntax.monad._
import scalaz.syntax.foldable._
#-scalaz
#+cats
import cats.Foldable
import cats.functor.Contravariant
import cats.implicits._
#-cats
#+fs2
import fs2.{ Stream => Process }
#-fs2

/** Module defining updates parameterized by input type. */
object update {

  /** Mixin trait for queries with diagnostic information. */
  trait UpdateDiagnostics {
    val sql: String
    val stackFrame: Option[StackTraceElement]
    def analysis: ConnectionIO[Analysis]
  }

  trait Update[A] extends UpdateDiagnostics { u =>

    def run(a: A): ConnectionIO[Int]

    def withGeneratedKeys[K: Composite](columns: String*)(a: A): Process[ConnectionIO, K]

    def withUniqueGeneratedKeys[K: Composite](columns: String*)(a: A): ConnectionIO[K]

    def updateMany[F[_]: Foldable](fa: F[A]): ConnectionIO[Int]

    // N.B. this what we want to implement, but updateManyWithGeneratedKeys is what we want to call
    protected def updateManyWithGeneratedKeysA[F[_]: Foldable, K: Composite](columns: String*)(as: F[A]): Process[ConnectionIO, K]

    def updateManyWithGeneratedKeys[K](columns: String*): Update.UpdateManyWithGeneratedKeysBuilder[A, K] =
      new Update.UpdateManyWithGeneratedKeysBuilder[A, K] {
        def apply[F[_]](as: F[A])(implicit F: Foldable[F], K: Composite[K]): Process[ConnectionIO, K] =
          updateManyWithGeneratedKeysA[F,K](columns: _*)(as)
      }

    def contramap[C](f: C => A): Update[C] =
      new Update[C] {
        val sql = u.sql
        val stackFrame = u.stackFrame
        def analysis: ConnectionIO[Analysis] = u.analysis
        def run(c: C) = u.run(f(c))
        def updateMany[F[_]: Foldable](fa: F[C]) = u.updateMany(fa.toList.map(f))
        protected def updateManyWithGeneratedKeysA[F[_]: Foldable, K: Composite](columns: String*)(cs: F[C]): Process[ConnectionIO, K] =
          u.updateManyWithGeneratedKeys(columns: _*)(cs.toList map f)
        def withGeneratedKeys[K: Composite](columns: String*)(c: C) =
          u.withGeneratedKeys(columns: _*)(f(c))
        def withUniqueGeneratedKeys[K: Composite](columns: String*)(c: C) =
          u.withUniqueGeneratedKeys(columns: _*)(f(c))
      }

    def toUpdate0(a: A): Update0 =
      new Update0 {
        val sql = u.sql
        val stackFrame = u.stackFrame
        def analysis = u.analysis
        def run = u.run(a)
        def withGeneratedKeys[K: Composite](columns: String*) = 
          u.withGeneratedKeys(columns: _*)(a)
        def withUniqueGeneratedKeys[K: Composite](columns: String*) =
          u.withUniqueGeneratedKeys(columns: _*)(a)
      }

  }

  object Update {

    /** 
     * Partial application hack to allow calling updateManyWithGeneratedKeys without passing the 
     * F[_] type argument explicitly.
     */
    trait UpdateManyWithGeneratedKeysBuilder[A, K] {
      def apply[F[_]](as: F[A])(implicit F: Foldable[F], K: Composite[K]): Process[ConnectionIO, K]
    }

    def apply[A: Composite](sql0: String, stackFrame0: Option[StackTraceElement] = None): Update[A] =
      new Update[A] {
        val sql = sql0
        val stackFrame = stackFrame0
        def analysis: ConnectionIO[Analysis] = HC.prepareUpdateAnalysis[A](sql)
        def run(a: A) = HC.prepareStatement(sql)(HPS.set(a) *> HPS.executeUpdate)
        def updateMany[F[_]: Foldable](fa: F[A]) =
          HC.prepareStatement(sql)(HPS.addBatchesAndExecute(fa))
        protected def updateManyWithGeneratedKeysA[F[_]: Foldable, K: Composite](columns: String*)(as: F[A]) =
          HC.updateManyWithGeneratedKeys[F,A,K](columns.toList)(sql, ().pure[PreparedStatementIO], as)
        def withGeneratedKeys[K: Composite](columns: String*)(a: A) =
          HC.updateWithGeneratedKeys[K](columns.toList)(sql, HPS.set(a))
        def withUniqueGeneratedKeys[K: Composite](columns: String*)(a: A) =
          HC.prepareStatementS(sql0, columns.toList)(HPS.set(a) *> HPS.executeUpdateWithUniqueGeneratedKeys)
      }

    implicit val updateContravariant: Contravariant[Update] =
      new Contravariant[Update] {
        def contramap[A, B](fa: Update[A])(f: B => A) = fa contramap f
      }

  }

  trait Update0 extends UpdateDiagnostics {
    def run: ConnectionIO[Int]
    def withGeneratedKeys[K: Composite](columns: String*): Process[ConnectionIO, K]
    def withUniqueGeneratedKeys[K: Composite](columns: String*): ConnectionIO[K]
  }

  object Update0 {

    def apply(sql0: String, stackFrame0: Option[StackTraceElement]): Update0 =
      new Update0 {
        val sql = sql0
        val stackFrame = stackFrame0
        def analysis: ConnectionIO[Analysis] = HC.prepareUpdateAnalysis0(sql)
        def run = HC.prepareStatement(sql)(HPS.executeUpdate)
        def withGeneratedKeys[K: Composite](columns: String*) =
          HC.updateWithGeneratedKeys(columns.toList)(sql, ().pure[PreparedStatementIO])
        def withUniqueGeneratedKeys[K: Composite](columns: String*) =
          HC.prepareStatementS(sql0, columns.toList)(HPS.executeUpdateWithUniqueGeneratedKeys)
      }

  }

}