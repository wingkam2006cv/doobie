// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.quill

import cats.effect._
import doobie._
import doobie.implicits._
import io.getquill._
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext

// https://github.com/tpolecat/doobie/issues/1067
class Issue1067 extends Specification {

  implicit def contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  lazy val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:world",
    "postgres", ""
  )

  val dc = new DoobieContext.Postgres(Literal)
  import dc._

  case class Country(name: String, indepYear: Option[Short])

  "Issue1067" should {
    "correctly select many countries, with a null in last position" in {
      val stmt     = quote { query[Country] }
      val actual   = run(stmt).transact(xa).unsafeRunSync()
      actual.count(_.indepYear.isDefined) should_== 192
      actual.count(_.indepYear.isEmpty) should_== 47
    }
  }

}