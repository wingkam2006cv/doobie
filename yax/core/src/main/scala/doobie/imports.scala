package doobie

import doobie.syntax.catchsql.ToDoobieCatchSqlOps
import doobie.syntax.catchable.ToDoobieCatchableOps

#+scalaz
import scalaz.{ Monad, Catchable, Unapply, Leibniz, Free, Functor }
import scalaz.stream.Process
#-scalaz
#+cats
import cats.{ Monad, Functor, Unapply }
import cats.free.Free
#-cats

/** Module of aliases for commonly-used types and syntax; use as `import doobie.imports._` */
object imports extends ToDoobieCatchSqlOps with ToDoobieCatchableOps {

  /**
   * Alias for `doobie.free.connection`.
   * @group Free Module Aliases
   */
  val FC   = doobie.free.connection

  /**
   * Alias for `doobie.free.statement`.
   * @group Free Module Aliases
   */
  val FS   = doobie.free.statement

  /**
   * Alias for `doobie.free.preparedstatement`.
   * @group Free Module Aliases
   */
  val FPS  = doobie.free.preparedstatement

  /**
   * Alias for `doobie.free.resultset`.
   * @group Free Module Aliases
   */
  val FRS  = doobie.free.resultset

  /**
   * Alias for `doobie.hi.connection`.
   * @group Hi Module Aliases
   */
  val HC   = doobie.hi.connection

  /**
   * Alias for `doobie.hi.drivermanager`.
   * @group Hi Module Aliases
   */
  val HDM  = doobie.hi.drivermanager

  /**
   * Alias for `doobie.hi.statement`.
   * @group Hi Module Aliases
   */
  val HS   = doobie.hi.statement

  /**
   * Alias for `doobie.hi.preparedstatement`.
   * @group Hi Module Aliases
   */
  val HPS  = doobie.hi.preparedstatement

  /**
   * Alias for `doobie.hi.resultset`.
   * @group Hi Module Aliases
   */
  val HRS  = doobie.hi.resultset

  /** @group Type Aliases */ type ConnectionIO[A]        = doobie.free.connection.ConnectionIO[A]
  /** @group Type Aliases */ type StatementIO[A]         = doobie.free.statement.StatementIO[A]
  /** @group Type Aliases */ type PreparedStatementIO[A] = doobie.free.preparedstatement.PreparedStatementIO[A]
  /** @group Type Aliases */ type ResultSetIO[A]         = doobie.free.resultset.ResultSetIO[A]

#+scalaz
  /** @group Syntax */
  implicit def toProcessOps[F[_]: Monad: Catchable: Capture, A](fa: Process[F, A]): doobie.syntax.process.ProcessOps[F, A] =
    new doobie.syntax.process.ProcessOps(fa)
#-scalaz

  /** @group Syntax */
  implicit def toSqlInterpolator(sc: StringContext): doobie.syntax.string.SqlInterpolator =
    new doobie.syntax.string.SqlInterpolator(sc)

  /** @group Syntax */
  implicit def toMoreConnectionIOOps[A](ma: ConnectionIO[A]): doobie.syntax.connectionio.MoreConnectionIOOps[A] =
    new doobie.syntax.connectionio.MoreConnectionIOOps(ma)

  /** @group Type Aliases */      type Meta[A] = doobie.util.meta.Meta[A]
  /** @group Companion Aliases */ val  Meta    = doobie.util.meta.Meta

  /** @group Type Aliases */      type Atom[A] = doobie.util.atom.Atom[A]
  /** @group Companion Aliases */ val  Atom    = doobie.util.atom.Atom

  /** @group Type Aliases */      type Capture[M[_]] = doobie.util.capture.Capture[M]
  /** @group Companion Aliases */ val  Capture       = doobie.util.capture.Capture

  /** @group Type Aliases */      type Composite[A] = doobie.util.composite.Composite[A]
  /** @group Companion Aliases */ val  Composite    = doobie.util.composite.Composite

  /** @group Type Aliases */      type Query[A,B] = doobie.util.query.Query[A,B]
  /** @group Companion Aliases */ val  Query      = doobie.util.query.Query

  /** @group Type Aliases */      type Update[A] = doobie.util.update.Update[A]
  /** @group Companion Aliases */ val  Update    = doobie.util.update.Update

  /** @group Type Aliases */      type Query0[A]  = doobie.util.query.Query0[A]
  /** @group Companion Aliases */ val  Query0     = doobie.util.query.Query0

  /** @group Type Aliases */      type Update0   = doobie.util.update.Update0
  /** @group Companion Aliases */ val  Update0   = doobie.util.update.Update0

  /** @group Type Aliases */      type SqlState = doobie.enum.sqlstate.SqlState
  /** @group Companion Aliases */ val  SqlState = doobie.enum.sqlstate.SqlState

  /** @group Type Aliases */      type Param[A] = doobie.syntax.string.Param[A]
  /** @group Companion Aliases */ val  Param    = doobie.syntax.string.Param

  /** @group Type Aliases */ type Transactor[M[_]] = doobie.util.transactor.Transactor[M]

  /** @group Companion Aliases */ val DriverManagerTransactor = doobie.util.transactor.DriverManagerTransactor
  /** @group Companion Aliases */ val DataSourceTransactor = doobie.util.transactor.DataSourceTransactor

  /** @group Type Aliases */      type IOLite[A] = doobie.util.iolite.IOLite[A]
  /** @group Companion Aliases */ val  IOLite    = doobie.util.iolite.IOLite

#+scalaz
  /** @group Typeclass Instances */
  implicit val NameCatchable = doobie.util.name.NameCatchable

  /** @group Typeclass Instances */
  implicit val NameCapture   = doobie.util.name.NameCapture
#-scalaz

  /**
   * Free monad derivation with correct shape to derive an instance for `Free[Coyoneda[F, ?], ?]`.
   * @group Hacks
   */
  implicit def freeMonadC[FT[_[_], _], F[_]](implicit ev: Functor[FT[F, ?]]): Monad[Free[FT[F,?], ?]] =
    Free.freeMonad[FT[F,?]]

  /**
   * Unapply with correct shape to unpack `Monad[Free[Coyoneda[F, ?], ?]]`.
   * @group Hacks
   */
  implicit def unapplyMMFA[TC[_[_]], M0[_[_], _], M1[_[_], _], F0[_], A0](implicit TC0: TC[M0[M1[F0,?], ?]]):
    Unapply[TC, M0[M1[F0,?], A0]] {
      type M[X] = M0[M1[F0,?], X]
      type A = A0
    } =
      new Unapply[TC, M0[M1[F0,?], A0]] {
        type M[X] = M0[M1[F0,?], X]
        type A = A0
        def TC = TC0
#+scalaz        
        def leibniz = Leibniz.refl
#-scalaz
#+cats
        def subst = ma => ma.asInstanceOf[M[A]] // for now
#-cats
      }

}
