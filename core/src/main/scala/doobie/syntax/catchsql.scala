package doobie.syntax

import scalaz.{ Monad, Catchable, \/, -\/, \/-, Unapply }
import doobie.util.{ catchsql => C }
import doobie.enum.sqlstate.SqlState
import java.sql.SQLException

/** Syntax for `Catchable` combinators defined in `util.catchsql`. */
object catchsql {

  class DoobieCatchSqlOps[M[_]: Monad: Catchable, A](self: M[A]) {

    def attemptSql: M[SQLException \/ A] =
      C.attemptSql(self)

    def attemptSqlState: M[SqlState \/ A] =
      C.attemptSqlState(self)

    def attemptSomeSqlState[B](f: PartialFunction[SqlState, B]): M[B \/ A] =
      C.attemptSomeSqlState(self)(f)

    def exceptSql(handler: SQLException => M[A]): M[A] =
      C.exceptSql(self)(handler)

    def exceptSqlState(handler: SqlState => M[A]): M[A] =
      C.exceptSqlState(self)(handler)

    def exceptSomeSqlState(pf: PartialFunction[SqlState, M[A]]): M[A] =
      C.exceptSomeSqlState(self)(pf)

    def onSqlException[B](action: M[B]): M[A] =
      C.onSqlException(self)(action)
    
  }

  trait ToDoobieCatchSqlOps0 {

    /** @group Syntax */
    implicit def toDoobieCatchSqlOpsUnapply[MA](ma: MA)(
      implicit M0: Unapply[Monad, MA],
               C0: Unapply[Catchable, MA]
    ): DoobieCatchSqlOps[M0.M, M0.A] =
      new DoobieCatchSqlOps[M0.M, M0.A](M0(ma))(M0.TC, C0.TC.asInstanceOf[Catchable[M0.M]])
  
  }

  trait ToDoobieCatchSqlOps extends ToDoobieCatchSqlOps0 {
  
    /** @group Syntax */
    implicit def toDoobieCatchSqlOps[M[_]: Monad: Catchable, A](ma: M[A]): DoobieCatchSqlOps[M, A] =
      new DoobieCatchSqlOps(ma)
  
  }

}
