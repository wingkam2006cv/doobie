package doobie.util

#+scalaz
import scalaz.concurrent.Task
import scalaz.effect.IO
#-scalaz

/** Module for a typeclass for monads with effect-capturing unit. */
object capture {

  trait Capture[M[_]] {
    def apply[A](a: => A): M[A]
  }

  object Capture {

    def apply[M[_]](implicit M: Capture[M]): Capture[M] = M

#+scalaz
    implicit val TaskCapture: Capture[Task] =
      new Capture[Task] {
        def apply[A](a: => A): Task[A] =
          Task.delay(a)
      }

    implicit val IOCapture: Capture[IO] =
      new Capture[IO] {
        def apply[A](a: => A): IO[A] =
          IO(a)
      }

#-scalaz
  }

}

