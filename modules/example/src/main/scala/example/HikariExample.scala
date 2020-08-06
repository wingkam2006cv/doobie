// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import doobie._
import doobie.hikari._
import doobie.implicits._

object HikariExample extends IOApp {

  // Typically you construct a transactor this way, using lifetime-managed thread pools.
  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      blocker <- Blocker[IO]
      xa <- HikariTransactor.newHikariTransactor[IO](
              "org.h2.Driver",
              "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
              "sa", "",
              ce, blocker
            )
    } yield xa

  def run(args: List[String]): IO[ExitCode] =
    transactor.use { xa =>
      FirstExample.examples.transact(xa).as(ExitCode.Success)
    }

}
