// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import cats.effect.{ Async, IO }
import doobie._, doobie.implicits._
import org.specs2.mutable.Specification
import scala.Predef._

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object transactorspec extends Specification {

  val q = sql"select 42".query[Int].unique

  def xa[A[_]: Async] = Transactor.fromDriverManager[A](
    "org.h2.Driver",
    "jdbc:h2:mem:queryspec;DB_CLOSE_DELAY=-1",
    "sa", ""
  )

  "transactor" should {

    "support cats.effect.IO" in {
      q.transact(xa[IO]).unsafeRunSync must_=== 42
    }

  }

}
