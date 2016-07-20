package doobie.util

#+scalaz
import scalaz.{ Monad, Catchable, \/ }
import scalaz.syntax.bifunctor._
import scalaz.syntax.monad._
#-scalaz
#+cats
import cats.Monad
import cats.data.{ Kleisli, Xor => \/ }
import cats.implicits._
#-cats

/** 
 * Module of additional combinators for `Catchable`, all defined in terms of `attempt`. Similar to 
 * those provided for `MonadCatchIO`. 
 */
object catchable {

#+cats
  trait Catchable[M[_]] {
    def attempt[A](ma: M[A]): M[Throwable \/ A]
    def fail[A](t: Throwable): M[A]
  }

  object Catchable {
    implicit def catsKleisliCatchable[M[_], E]: Catchable[Kleisli[M, E, ?]] = null // TODO
  }
#-cats

  /** Like `attempt` but catches (and maps) only where defined. */
  def attemptSome[M[_]: Monad, A, B](ma: M[A])(p: PartialFunction[Throwable, B])(implicit c: Catchable[M]): M[B \/ A] =
    c.attempt(ma).map(_.leftMap(e => p.lift(e).getOrElse(throw e)))

  /** Executes the handler, for exceptions propagating from `ma`. */
  def except[M[_]: Monad, A](ma: M[A])(handler: Throwable => M[A])(implicit c: Catchable[M]): M[A] =
    c.attempt(ma).flatMap(_.bimap(handler, _.pure[M]).merge)

  /** Executes the handler where defined, for exceptions propagating from `ma`. */
  def exceptSome[M[_]: Monad: Catchable, A](ma: M[A])(pf: PartialFunction[Throwable, M[A]]): M[A] =
    except(ma)(e => pf.lift(e).getOrElse((throw e): M[A]))

  /** Like "finally", but only performs the final action if there was an exception. */
  def onException[M[_]: Monad, A, B](ma: M[A])(action: M[B])(implicit c: Catchable[M]): M[A] =
    except(ma)(e => action *> c.fail(e))

  /** Always execute `sequel` following `ma`; generalizes `finally`. */
  def ensuring[M[_]: Monad: Catchable, A, B](ma: M[A])(sequel: M[B]): M[A] =
    onException(ma)(sequel) <* sequel

}

