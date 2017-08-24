// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.postgres.hi

import cats.implicits._
import java.io.{ File, OutputStream, InputStream }

object largeobjectmanager {
  import implicits._

  val createLO: LargeObjectManagerIO[Long] =
    PFLOM.createLO

  def createLO(a: Int): LargeObjectManagerIO[Long] =
    PFLOM.createLO(a)

  def delete(a: Long): LargeObjectManagerIO[Unit] =
    PFLOM.delete(a)

  def open[A](a: Long, b: Int)(k: LargeObjectIO[A]): LargeObjectManagerIO[A] =
    PFLOM.open(a, b) >>= (PFLOM.embed(_, k <* PFLO.close))

  def open[A](a: Long)(k: LargeObjectIO[A]): LargeObjectManagerIO[A] =
    PFLOM.open(a) >>= (PFLOM.embed(_, k <* PFLO.close))

  def unlink(a: Long): LargeObjectManagerIO[Unit] =
    PFLOM.unlink(a)

  def createLOFromFile(blockSize: Int, file: File): LargeObjectManagerIO[Long] =
    createLO >>= { oid => open(oid)(PHLO.copyFromFile(blockSize, file)).as(oid) }

  def createFileFromLO(blockSize: Int, oid: Long, file: File): LargeObjectManagerIO[Unit] =
    open(oid)(PHLO.copyToFile(blockSize, file))

  def createLOFromStream(blockSize: Int, is: InputStream): LargeObjectManagerIO[Long] =
    PHLOM.createLO >>= { oid =>
      PHLOM.open(oid)(PHLO.copyFromStream(blockSize, is)).as(oid)
    }

  def createStreamFromLO(blockSize: Int, oid: Long, os: OutputStream): LargeObjectManagerIO[Unit] =
    open(oid)(PHLO.copyToStream(blockSize, os))
}
