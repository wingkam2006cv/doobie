// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.implicits._
import cats.effect.{ IO, IOApp, ExitCode }
import doobie._
import doobie.implicits._
import doobie.postgres._
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.io.InputStream
import fs2._

object PostgresCopyInCsv extends IOApp {

  def putStrLn(s: String): IO[Unit] = IO(println(s))

  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver", "jdbc:postgresql://localhost/postgres", "postgres", "super-secret"
  )

  val csv = """name,food
                |piglet,haycorns
                |eeyore,thistles
                |pooh,honey
                |tigger,extract of malt""".stripMargin

  // The postgres driver expects an InputStream containing the data to load
  // We wrap this in IO because a BAIS allocated visible mutable state
  val is: IO[InputStream] = IO.delay(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))).widen[InputStream]

  // We can also convert a Stream[F, Byte] into an input stream to pass to the pg driver
  val byteStream = Stream.emit(csv).through(text.utf8Encode).covary[IO]

  // Create a temorary table to hold the input data
  val createTable: ConnectionIO[Int] = sql"CREATE TEMP TABLE favorite_foods(name TEXT, food TEXT)".update.run

  def copyIn(is: InputStream): ConnectionIO[Long] = {
    // construct a CopyManagerIO with the postgres extensions
    // The structure of the sql expression itself is described in https://www.postgresql.org/docs/current/sql-copy.html
    // Here we describe our input format as csv with a header and the standard delimiter
    // The header line is ignored by postgres, column ordering comes from the table statement
    val copyInIO: CopyManagerIO[Long] = PFCM.copyIn("COPY favorite_foods(name, food) FROM STDIN WITH (FORMAT CSV, HEADER, DELIMITER ',')", is)
    // Lift the CopyManagerIO into ConnectionIO so it can be transacted
    PHC.pgGetCopyAPI(copyInIO)
  }

  val simpleExample = 
    // Construct a copy from an InputStream
    is.flatMap(is => (createTable >> copyIn(is)).transact(xa))
      .map(_.toString)
      .flatMap(ct => putStrLn(show"loaded $ct from InputStream"))

  val fromByteStreamExample = 
    // Construct a copy by converting a Stream[IO, Byte] into an InputStream
    io.toInputStreamResource(byteStream)
      .use(is => (createTable >> copyIn(is)).transact(xa))
      .map(_.toString)
      .flatMap(ct => putStrLn(show"loaded $ct from Stream[IO, Byte]"))

  def run(args: List[String]): IO[ExitCode] = {
    // Should print "loaded 4 from Stream[IO, Byte]" twice
    simpleExample >> fromByteStreamExample
      .as(ExitCode.Success)
  }
    
    
}
