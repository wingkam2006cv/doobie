package doobie.example

import java.io.File

import scalaz._, Scalaz._
import scalaz.concurrent.Task

import doobie.free.{ drivermanager => DM }
import doobie.free.{ connection => C }
import doobie.free.{ preparedstatement => PS }
import doobie.free.{ resultset => RS }
import doobie.std.task._

// JDBC program using the low-level API
object FreeUsage {

  case class CountryCode(code: String)
  
  def main(args: Array[String]): Unit =
    tmain.translate[Task].run

  val tmain: DM.DriverManagerIO[Unit] = 
    for {
      _ <- DM.delay(Class.forName("org.h2.Driver"))
      c <- DM.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
      a <- DM.liftConnection(c, examples) // except(t => IO(t.toString))
      _ <- DM.delay(Console.println(a))
    } yield ()

  def examples: C.ConnectionIO[String] =
    for {
      _ <- C.delay(println("Loading database..."))
      _ <- loadDatabase(new File("example/world.sql"))
      s <- speakerQuery("French", 0)
      _ <- s.traverseU(a => C.delay(println(a)))
    } yield "Ok"

  def loadDatabase(f: File): C.ConnectionIO[Unit] =
    for {
      ps <- C.prepareStatement("RUNSCRIPT FROM ? CHARSET 'UTF-8'")
      _  <- C.liftPreparedStatement(ps, PS.setString(1, f.getName) >> PS.execute)
    } yield ()

  def speakerQuery(s: String, p: Int): C.ConnectionIO[List[CountryCode]] =
    for {
      ps <- C.prepareStatement("SELECT COUNTRYCODE FROM COUNTRYLANGUAGE WHERE LANGUAGE = ? AND PERCENTAGE > ?")
      l  <- C.liftPreparedStatement(ps, 
        for {
          _  <- PS.setString(1, s)
          _  <- PS.setInt(2, p)
          rs <- PS.executeQuery
          l  <- PS.liftResultSet(rs, unroll(RS.getString(1).map(CountryCode(_))))
        } yield l)
    } yield l

  def unroll[A](a: RS.ResultSetIO[A]): RS.ResultSetIO[List[A]] = {
    def unroll0(as: List[A]): RS.ResultSetIO[List[A]] =
      RS.next >>= {
        case false => as.point[RS.ResultSetIO]
        case true  => a >>= { a => unroll0(a :: as) }
      }
    unroll0(Nil).map(_.reverse)
  }

}
