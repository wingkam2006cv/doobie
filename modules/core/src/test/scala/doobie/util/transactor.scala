// Copyright (c) 2013-2018 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import cats.effect.{ Async, ContextShift, Effect, IO }
import cats.effect.syntax.effect._
import doobie._, doobie.implicits._
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object transactorspec extends Specification {

  val q = sql"select 42".query[Int].unique

  implicit def contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  def xa[A[_]: Async: ContextShift] = Transactor.fromDriverManager[A](
    "org.h2.Driver",
    "jdbc:h2:mem:queryspec;DB_CLOSE_DELAY=-1",
    "sa", ""
  )

  "transactor" should {

    "support cats.effect.IO" in {
      q.transact(xa[IO]).unsafeRunSync must_=== 42
    }

    "support scalaz.zio.interop.Task" in {
      implicit val E: Effect[scalaz.zio.interop.Task] = scalaz.zio.interop.catz.taskEffectInstances
      implicit val contextShift: ContextShift[scalaz.zio.interop.Task] = scalaz.zio.interop.catz.ioContextShift
      q.transact(xa[scalaz.zio.interop.Task]).toIO.unsafeRunSync() must_=== 42
    }

  }

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  class ConnectionTracker {
    var connections = List.empty[java.sql.Connection]

    def track[F[_]: Async: ContextShift](xa: Transactor[F]) = {
      def withA(t: doobie.util.transactor.Transactor[F]): Transactor.Aux[F, t.A] = {
        Transactor.connect.modify(t, f => a => {
          f(a).map { conn =>
            connections = conn :: connections
            conn
          }
        })
      }
      withA(xa)
    }
  }

  "Connection lifecycle" >> {

    "Connection.close should be called on success" in {
      val tracker = new ConnectionTracker
      val transactor = tracker.track(xa[IO])
      sql"select 1".query[Int].unique.transact(transactor).unsafeRunSync
      tracker.connections.map(_.isClosed) must_== List(true)
    }

    "Connection.close should be called on failure" in {
      val tracker = new ConnectionTracker
      val transactor = tracker.track(xa[IO])
      sql"abc".query[Int].unique.transact(transactor).attempt.unsafeRunSync.toOption must_== None
      tracker.connections.map(_.isClosed) must_== List(true)
    }

  }

  "Connection lifecycle (streaming)" >> {

    "Connection.close should be called on success" in {
      val tracker = new ConnectionTracker
      val transactor = tracker.track(xa[IO])
      sql"select 1".query[Int].stream.compile.toList.transact(transactor).unsafeRunSync
      tracker.connections.map(_.isClosed) must_== List(true)
    }

    "Connection.close should be called on failure" in {
      val tracker = new ConnectionTracker
      val transactor = tracker.track(xa[IO])
      sql"abc".query[Int].stream.compile.toList.transact(transactor).attempt.unsafeRunSync.toOption must_== None
      tracker.connections.map(_.isClosed) must_== List(true)
    }

  }

}
