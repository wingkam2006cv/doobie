// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.postgres

import doobie._, doobie.implicits._
import doobie.postgres.implicits._
import org.specs2.mutable.Specification


class syntaxspec extends Specification {

  "syntax" should {

    "not overflow the stack on direct recursion" in {
      def prog: ConnectionIO[Unit] = FC.delay(()).onUniqueViolation(prog)
      prog
      true
    }

  }

}
