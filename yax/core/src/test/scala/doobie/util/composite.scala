package doobie.util

import shapeless._, shapeless.test._, shapeless.record._
import doobie.imports._
import org.specs2.mutable.Specification

object compositespec extends Specification {

  case class Woozle(a: (String, Int), b: Int :: String :: HNil, c: Boolean)

  case class LenStr1(n: Int, s: String)

  case class LenStr2(n: Int, s: String)
  object LenStr2 {
    implicit val LenStrMeta =
      Meta[String].xmap[LenStr2](s => LenStr2(s.length, s), _.s)
  }

  "Composite" should {

    "exist for some fancy types" in {
      Composite[Int]
      Composite[(Int, Int)]
      Composite[(Int, Int, String)]
      Composite[(Int, (Int, String))]
      Composite[Woozle]

      // https://github.com/tpolecat/doobie/pull/126 was reverted because these
      // derivations were failing with SOE
      Composite[(Woozle, String)]
      Composite[(Int, Woozle :: Woozle :: String :: HNil)]

      true
    }

    "exist for shapeless record types" in {

      type DL = (Double, Long)
      type A = Record.`'foo -> Int, 'bar -> String, 'baz -> DL, 'quz -> Woozle`.T

      Composite[A]
      Composite[(A, A)]

      true
    }

    "select multi-column instance by default" in {
      Composite[LenStr1].length must_== 2
    }

    "select 1-column instance when available" in {
      Composite[LenStr2].length must_== 1
    }

    // "work for products of ludicrous size (128)" in {
    //   Composite[
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int ::
    //     Int :: Int :: Int :: Int :: Int :: Int :: Int :: Int :: HNil]
    //   true
    // }

    // "work for specific example from #217" in {
    //   trait LocalDateTime
    //   implicit val MetaLocalDateTime: Meta[LocalDateTime] = null
    //   Composite[
    //     Option[String] :: Option[String] :: Option[String] :: Option[LocalDateTime] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[LocalDateTime] :: Option[LocalDateTime] ::
    //     Option[LocalDateTime] :: Option[LocalDateTime] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[BigDecimal] ::
    //     Option[String] :: Option[BigDecimal] :: Option[BigDecimal] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[BigDecimal] :: Option[BigDecimal] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[String] :: Option[String] ::
    //     Option[String] :: Option[String] :: Option[BigDecimal] :: Option[BigDecimal] ::
    //     Option[String] :: HNil]
    //   true
    // }

  }

}
