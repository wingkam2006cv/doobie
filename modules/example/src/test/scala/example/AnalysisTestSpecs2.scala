// Copyright (c) 2013-2018 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect.IO
import doobie._
import doobie.specs2.analysisspec._
import org.specs2.mutable.Specification

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object AnalysisTestSpecs2 extends Specification with IOChecker {
  val transactor = Transactor.fromDriverManager[IO]("org.postgresql.Driver", "jdbc:postgresql:world", "postgres", "")
  // Commented tests fail!
  // check(AnalysisTest.speakerQuery(null, 0))
  check(AnalysisTest.speakerQuery2)
  check(AnalysisTest.arrayTest)
  // check(AnalysisTest.arrayTest2)
  check(AnalysisTest.pointTest)
  // check(AnalysisTest.pointTest2)
  // check(AnalysisTest.update("foo", 42))
  check(AnalysisTest.update2)
}
