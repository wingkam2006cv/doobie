// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.h2

import java.time.temporal.ChronoField.NANO_OF_SECOND
import java.util.UUID

import cats.effect.{ContextShift, IO}
import doobie._
import doobie.h2.implicits._
import doobie.implicits._
import doobie.util.arbitraries.SQLArbitraries._
import doobie.util.arbitraries.StringArbitraries._
import doobie.util.arbitraries.TimeArbitraries._
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Gen}
import doobie.implicits.javasql._
import doobie.implicits.javatime.{JavaTimeInstantMeta => NewJavaTimeInstantMeta, _}
import doobie.implicits.legacy.instant.{JavaTimeInstantMeta => LegacyJavaTimeInstantMeta}
import scala.concurrent.ExecutionContext

// Establish that we can read various types. It's not very comprehensive as a test, bit it's a start.
class h2typesspec extends munit.ScalaCheckSuite {

  implicit def contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  val xa = Transactor.fromDriverManager[IO](
    "org.h2.Driver",
    "jdbc:h2:mem:ch3;DB_CLOSE_DELAY=-1",
    "sa", ""
  )

  def inOut[A: Put : Get](col: String, a: A): ConnectionIO[A] =
    for {
      _ <- Update0(s"CREATE LOCAL TEMPORARY TABLE TEST (value $col)", None).run
      _ <- sql"INSERT INTO TEST VALUES ($a)".update.run
      a0 <- sql"SELECT value FROM TEST".query[A].unique
    } yield (a0)

  def inOutOpt[A: Put : Get](col: String, a: Option[A]): ConnectionIO[Option[A]] =
    for {
      _ <- Update0(s"CREATE LOCAL TEMPORARY TABLE TEST (value $col)", None).run
      _ <- sql"INSERT INTO TEST VALUES ($a)".update.run
      a0 <- sql"SELECT value FROM TEST".query[Option[A]].unique
    } yield (a0)

  def testInOut[A](col: String)(implicit m: Get[A], p: Put[A], arbitrary: Arbitrary[A]) = {
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as ${m.typeStack}") {
      forAll { (t: A) => assertEquals(inOut(col, t).transact(xa).attempt.unsafeRunSync(), Right(t)) }
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (Some)") {
      forAll { (t: A) => assertEquals(inOutOpt[A](col, Some(t)).transact(xa).attempt.unsafeRunSync(), Right(Some(t))) }
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (None)") {
      assertEquals(inOutOpt[A](col, None).transact(xa).attempt.unsafeRunSync(), Right(None))
    }
  }

  def testInOutWithCustomGen[A](col: String, gen: Gen[A])(implicit m: Get[A], p: Put[A]) = {
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as ${m.typeStack}") {
      forAll(gen) { (t: A) => assertEquals(  inOut(col, t).transact(xa).attempt.unsafeRunSync(), Right(t)) }
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (Some)") {
      forAll(gen) { (t: A) => assertEquals(  inOutOpt[A](col, Some(t)).transact(xa).attempt.unsafeRunSync(), Right(Some(t))) }
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (None)") {
      assertEquals(  inOutOpt[A](col, None).transact(xa).attempt.unsafeRunSync(), Right(None))
    }
  }

  def testInOutWithCustomTransform[A](col: String)(f: A => A)(implicit m: Get[A], p: Put[A], arbitrary: Arbitrary[A]) = {
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as ${m.typeStack}") {
      forAll { (t: A) => assertEquals(  inOut(col, f(t)).transact(xa).attempt.unsafeRunSync(), Right(f(t))) }
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (Some)") {
      forAll { (t: A) => assertEquals(  inOutOpt[A](col, Some(f(t))).transact(xa).attempt.unsafeRunSync(), Right(Some(f(t)))) }
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (None)") {
      assertEquals(  inOutOpt[A](col, None).transact(xa).attempt.unsafeRunSync(), Right(None))
    }
  }

  def skip(col: String, msg: String = "not yet implemented") =
    test(s"Mapping for $col - $msg".ignore) {}

  testInOut[Int]("INT")
  testInOut[Boolean]("BOOLEAN")
  testInOut[Byte]("TINYINT")
  testInOut[Short]("SMALLINT")
  testInOut[Long]("BIGINT")
  testInOut[BigDecimal]("DECIMAL")

  /*
      TIME
      If fractional seconds precision is specified it should be from 0 to 9, 0 is default.
   */
  testInOut[java.sql.Time]("TIME")
  testInOutWithCustomTransform[java.time.LocalTime]("TIME")(_.withNano(0))

  testInOut[java.sql.Date]("DATE")
  testInOut[java.time.LocalDate]("DATE")(implicitly, implicitly, Arbitrary(localDateBcAndAdGen))

  /*
      TIMESTAMP
      If fractional seconds precision is specified it should be from 0 to 9, 6 is default.
   */
  testInOutWithCustomTransform[java.sql.Timestamp]("TIMESTAMP") { ts => ts.setNanos(0); ts }
  testInOutWithCustomTransform[java.time.LocalDateTime]("TIMESTAMP")(_.withNano(0))
  testInOutWithCustomTransform[java.time.Instant]("TIMESTAMP")(_.`with`(NANO_OF_SECOND, 0))(
    LegacyJavaTimeInstantMeta.get, LegacyJavaTimeInstantMeta.put, arbitraryInstant
  )
  testInOutWithCustomTransform[java.time.Instant]("TIMESTAMP")(_.`with`(NANO_OF_SECOND, 0))(
    NewJavaTimeInstantMeta.get, NewJavaTimeInstantMeta.put, arbitraryInstant
  )

  /*
      TIME WITH TIMEZONE
      If fractional seconds precision is specified it should be from 0 to 9, 6 is default.
   */
  testInOutWithCustomTransform[java.time.OffsetTime]("TIME WITH TIME ZONE")(_.withNano(0))

  /*
      TIMESTAMP WITH TIME ZONE
      If fractional seconds precision is specified it should be from 0 to 9, 6 is default.
   */
  testInOutWithCustomTransform[java.time.OffsetDateTime]("TIMESTAMP WITH TIME ZONE")(_.withNano(0))
  testInOutWithCustomTransform[java.time.ZonedDateTime]("TIMESTAMP WITH TIME ZONE")(_.withFixedOffsetZone().withNano(0))

  testInOut[List[Byte]]("BINARY")
  skip("OTHER")
  testInOut[String]("VARCHAR")
  testInOutWithCustomGen[String]("CHAR(3)", nLongString(3))
  skip("BLOB")
  skip("CLOB")
  testInOut[UUID]("UUID")
  testInOut[List[Int]]("ARRAY")
  testInOut[List[String]]("ARRAY")
  skip("GEOMETRY")

  test("Mapping for Boolean should pass query analysis for unascribed 'true'") {
    val a = sql"select true".query[Boolean].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.alignmentErrors, Nil)
  }
  test("Mapping for Boolean should pass query analysis for ascribed BIT") {
    val a = sql"select true::BIT".query[Boolean].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.alignmentErrors, Nil)
  }
  test("Mapping for Boolean should pass query analysis for ascribed BOOLEAN") {
    val a = sql"select true::BIT".query[Boolean].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.alignmentErrors, Nil)
  }

  test("Mapping for UUID should pass query analysis for unascribed UUID") {
    val a = sql"select random_uuid()".query[UUID].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.alignmentErrors, Nil)
  }
  test("Mapping for UUID should pass query analysis for ascribed UUID") {
    val a = sql"select random_uuid()::UUID".query[UUID].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.alignmentErrors, Nil)
  }

}
