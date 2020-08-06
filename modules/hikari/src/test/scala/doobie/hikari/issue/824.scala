// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.hikari.issue

import cats.effect._
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari._
import doobie.implicits._
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random
import java.util.concurrent.Executors


class `824` extends Specification {

  implicit def contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  implicit def timer: Timer[IO] =
    IO.timer(ExecutionContext.global)

  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](16) // our connect EC
      te <- Resource.liftF(IO.delay(Executors.newCachedThreadPool)) // our transaction EC
      xa <- HikariTransactor.newHikariTransactor[IO](
              "org.h2.Driver",                        // driver classname
              "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",   // connect URL
              "sa",                                   // username
              "",                                     // password
              ce,                                     // await connection here
              Blocker.liftExecutorService(te)         // execute JDBC operations here
            )
    } yield xa

    // Show the state of the pool
    @SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
    def report(ds: HikariDataSource): IO[Unit] =
      IO {
        val mx = ds.getHikariPoolMXBean; import mx._
        println(s"Idle: $getIdleConnections, Active: $getActiveConnections, Total: $getTotalConnections, Waiting: $getThreadsAwaitingConnection")
      }

  // Yield final active connections within the use block, as well as total connections after use
  // block. Both should be zero
  val prog: IO[(Int, Int)] =
    transactor.use { xa =>


      // Kick off a concurrent transaction, reporting the pool state on exit
      val random: IO[Fiber[IO, Unit]] =
        for {
          d <- IO(Random.nextInt(200) milliseconds)
          f <- (IO.sleep(d) *> report(xa.kernel)).to[ConnectionIO].transact(xa).start
        } yield f

      // Run a bunch of transactions at once, then return the active connection count
      for {
        _  <- IO(xa.kernel.setMaximumPoolSize(10)) // max connections
        _  <- ().pure[ConnectionIO].transact(xa)   // do this once to init the MBean
        _  <- report(xa.kernel)
        fs <- random.replicateA(50)
        _  <- fs.traverse(_.join)
        _  <- report(xa.kernel)
      } yield (xa.kernel.getHikariPoolMXBean.getActiveConnections, xa.kernel)

    } flatMap { case (n, ds) =>

      // One final report to show that all connections are disposed
      report(ds) *> IO((n, ds.getHikariPoolMXBean.getTotalConnections))

    }

  "HikariTransactor" should {
    "close connections logically within `use` block and physically afterward." in {
      prog.unsafeRunSync() must_== ((0, 0))
    }
  }

}
