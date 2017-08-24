// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.postgres.hi

trait Modules {
  lazy val PHPC  = pgconnection
  lazy val PHC   = connection
  lazy val PHLO  = largeobject
  lazy val PHLOM = largeobjectmanager
}
