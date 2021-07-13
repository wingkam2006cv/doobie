// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.munit

import cats.effect.{ ContextShift, IO }
import doobie.syntax.string._
import doobie.util.Read
import doobie.util.transactor.Transactor
import munit._
import scala.concurrent.ExecutionContext

trait CheckerChecks[M[_]] extends FunSuite with Checker[M] {

  implicit def contextShift: ContextShift[M]

  lazy val transactor = Transactor.fromDriverManager[M](
    "org.h2.Driver",
    "jdbc:h2:mem:queryspec;DB_CLOSE_DELAY=-1",
    "sa", ""
  )

  test("trivial") { check(sql"select 1".query[Int]) }

  test("fail".fail) { check(sql"select 1".query[String]) }

  final case class Foo[F[_]](x: Int)

  test ("trivial case-class"){ check(sql"select 1".query[Foo[cats.Id]]) }

  test("Read should select correct columns when combined with `product`") {
    import cats.syntax.all._
    import doobie.implicits._

    val ri = Read[Int]
    val rs = Read[String]

    // tupled use product under the hood
    val combined: Read[(Int, String)] = (ri, rs).tupled

    check(sql"SELECT 1, '2'".query(combined))
  }

  test("Read should select correct columns for checking when combined with `ap`") {
    val readInt = Read[(Int, Int)]
    val readIntToInt: Read[Tuple2[Int, Int] => String] =
      Read[(String, String)].map(i => k => s"$i,$k")

    val combined: Read[String] = readInt.ap(readIntToInt)

    check(sql"SELECT '1', '2', 3, 4".query(combined))
  }

}

class IOCheckerCheck extends CheckerChecks[IO] with IOChecker {
  def contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)
}
