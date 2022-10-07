// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.postgres.circe

import cats.effect.IO
import doobie._
import doobie.implicits._
import io.circe.{Decoder, Encoder, Json}

class PGJsonSuite extends munit.FunSuite {

  import cats.effect.unsafe.implicits.global

  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:world",
    "postgres", ""
  )

  def inOut[A: Write: Read](col: String, a: A) =
    for {
      _  <- Update0(s"CREATE TEMPORARY TABLE TEST (value $col)", None).run
      a0 <- Update[A](s"INSERT INTO TEST VALUES (?)", None).withUniqueGeneratedKeys[A]("value")(a)
    } yield a0

  @SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
  def testInOut[A](col: String, a: A, t: Transactor[IO])(implicit m: Get[A], p: Put[A]) = {
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as ${m.typeStack}") {
      assertEquals(inOut(col, a).transact(t).attempt.unsafeRunSync(), Right(a))
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (Some)") {
      assertEquals(inOut[Option[A]](col, Some(a)).transact(t).attempt.unsafeRunSync(), Right(Some(a)))
    }
    test(s"Mapping for $col as ${m.typeStack} - write+read $col as Option[${m.typeStack}] (None)") {
      assertEquals(inOut[Option[A]](col, None).transact(t).attempt.unsafeRunSync(), Right(None))
    }
  }

  {
    import doobie.postgres.circe.json.implicits._
    testInOut("json", Json.obj("something" -> Json.fromString("Yellow")), xa)
  }

  {
    import doobie.postgres.circe.jsonb.implicits._
    testInOut("jsonb", Json.obj("something" -> Json.fromString("Yellow")), xa)
  }


  // Explicit Type Checks

  test("json should check ok for read") {
    import doobie.postgres.circe.json.implicits._

    val a = sql"select '{}' :: json".query[Json].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.columnTypeErrors, Nil)
  }
  test("json should check ok for write") {
    import doobie.postgres.circe.json.implicits._
    val a = sql"select ${Json.obj()} :: json".query[Json].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.parameterTypeErrors, Nil)
  }

  test("jsonb should check ok for read") {
    import doobie.postgres.circe.jsonb.implicits._
    val a = sql"select '{}' :: jsonb".query[Json].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.columnTypeErrors, Nil)
  }

  test("jsonb should check ok for write") {
    import doobie.postgres.circe.jsonb.implicits._
    val a = sql"select ${Json.obj()} :: jsonb".query[Json].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.parameterTypeErrors, Nil)
  }

  // Encoder / Decoders
  private case class Foo(x: Json)
  private object Foo{
    import doobie.postgres.circe.json.implicits._
    implicit val fooEncoder: Encoder[Foo] = Encoder[Json].contramap(_.x)
    implicit val fooDecoder: Decoder[Foo] = Decoder[Json].map(Foo(_))
    implicit val fooGet : Get[Foo] = pgDecoderGetT[Foo]
    implicit val fooPut : Put[Foo] = pgEncoderPutT[Foo]
  }

  test("fooGet should check ok for read") {
    val a = sql"select '{}' :: json".query[Foo].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.columnTypeErrors, Nil)
  }
  test("fooPut check ok for write") {
    val a = sql"select ${Foo(Json.obj())} :: json".query[Foo].analysis.transact(xa).unsafeRunSync()
    assertEquals(a.parameterTypeErrors, Nil)
  }

}
