// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.util

import cats.data.NonEmptyList
import cats.implicits._
import doobie._, doobie.implicits._
import org.specs2.mutable.Specification
import cats.effect.ContextShift
import scala.concurrent.ExecutionContext
import cats.effect.IO


class fragmentsspec extends Specification {
  import Fragments._

  implicit def contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  val xa = Transactor.fromDriverManager[IO](
    "org.h2.Driver",
    "jdbc:h2:mem:queryspec;DB_CLOSE_DELAY=-1",
    "sa", ""
  )

  "Fragments" >> {

    val nel  = List(1,2,3).toNel.getOrElse(sys.error("unpossible"))
    val fs   = List(1,2,3).map(n => fr"$n")
    val ofs  = List(1,2,3).map(n => Some(fr"$n").filter(_ => n % 2 =!= 0))

    "in for one column" in {
      in(fr"foo", nel).query[Unit].sql must_== "foo IN (?, ?, ?) "
    }

    "in for two columns" in {
      in(fr"foo", NonEmptyList.of((1, true), (2, false))).query[Unit].sql must_== "foo IN ((?,?), (?,?)) "
    }

    "notIn" in {
      notIn(fr"foo", nel).query[Unit].sql must_== "foo NOT IN (?, ?, ?) "
    }

    "and (many)" in {
      and(fs: _*).query[Unit].sql must_== "(? ) AND (? ) AND (? ) "
    }

    "and (single)" in {
      and(fs(0)).query[Unit].sql must_== "(? ) "
    }

    "and (empty)" in {
      and().query[Unit].sql must_== ""
    }

    "andOpt (many)" in {
      andOpt(ofs: _*).query[Unit].sql must_== "(? ) AND (? ) "
    }

    "andOpt (one)" in {
      andOpt(ofs(0)).query[Unit].sql must_== "(? ) "
    }

    "andOpt (none)" in {
      andOpt(None, None).query[Unit].sql must_== ""
    }

    "or (many)" in {
      or(fs: _*).query[Unit].sql must_== "(? ) OR (? ) OR (? ) "
    }

    "or (single)" in {
      or(fs(0)).query[Unit].sql must_== "(? ) "
    }

    "or (empty)" in {
      or().query[Unit].sql must_== ""
    }

    "orOpt (many)" in {
      orOpt(ofs: _*).query[Unit].sql must_== "(? ) OR (? ) "
    }

    "orOpt (one)" in {
      orOpt(ofs(0)).query[Unit].sql must_== "(? ) "
    }

    "orOpt (none)" in {
      orOpt(None, None).query[Unit].sql must_== ""
    }

    "whereAnd (many)" in {
      whereAnd(fs: _*).query[Unit].sql must_== "WHERE (? ) AND (? ) AND (? ) "
    }

    "whereAnd (single)" in {
      whereAnd(fs(0)).query[Unit].sql must_== "WHERE (? ) "
    }

    "whereAnd (empty)" in {
      whereAnd().query[Unit].sql must_== ""
    }

    "whereAndOpt (many)" in {
      whereAndOpt(ofs: _*).query[Unit].sql must_== "WHERE (? ) AND (? ) "
    }

    "whereAndOpt (one)" in {
      whereAndOpt(ofs(0)).query[Unit].sql must_== "WHERE (? ) "
    }

    "whereAndOpt (none)" in {
      whereAndOpt(None, None).query[Unit].sql must_== ""
    }

    "whereOr (many)" in {
      whereOr(fs: _*).query[Unit].sql must_== "WHERE (? ) OR (? ) OR (? ) "
    }

    "whereOr (single)" in {
      whereOr(fs(0)).query[Unit].sql must_== "WHERE (? ) "
    }

    "whereOr (empty)" in {
      whereOr().query[Unit].sql must_== ""
    }

    "whereOrOpt (many)" in {
      whereOrOpt(ofs: _*).query[Unit].sql must_== "WHERE (? ) OR (? ) "
    }

    "whereOrOpt (one)" in {
      whereOrOpt(ofs(0)).query[Unit].sql must_== "WHERE (? ) "
    }

    "whereOrOpt (none)" in {
      whereOrOpt(None, None).query[Unit].sql must_== ""
    }

    case class Person(name: String, age: Int)
    case class Contact(person: Person, address: Option[String])

    "values (1)" in {
      val c = Contact(Person("Bob", 42), Some("addr"))
      val f = fr"select" ++ Fragments.values(c)
      f.query[Contact].unique.transact(xa).unsafeRunSync() must_== c
    }

    "values (2)" in {
      val c = Contact(Person("Bob", 42), None)
      val f = fr"select" ++ Fragments.values(c)
      f.query[Contact].unique.transact(xa).unsafeRunSync() must_== c
    }

  }

}
