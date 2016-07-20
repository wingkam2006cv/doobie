package doobie.example

import java.io.File

import doobie.util.iolite._
import doobie.free.{ drivermanager => DM }
import doobie.free.{ connection => C }
import doobie.free.{ preparedstatement => PS }
import doobie.free.{ resultset => RS }
import doobie.syntax.catchable.ToDoobieCatchableOps._

#+scalaz
import scalaz.Scalaz._
#-scalaz
#+cats
import cats.implicits._
#-cats

// JDBC program using the low-level API
object FreeUsage {

  case class CountryCode(code: String)
  
  def main(args: Array[String]): Unit = 
    tmain.trans[IOLite].unsafePerformIO

  val tmain: DM.DriverManagerIO[Unit] = 
    for {
      _ <- DM.delay(Class.forName("org.h2.Driver"))
      c <- DM.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
      a <- DM.lift(c, examples ensuring C.close).except(t => t.toString.pure[DM.DriverManagerIO])
      _ <- DM.delay(Console.println(a))
    } yield ()

  def examples: C.ConnectionIO[String] =
    for {
      _ <- C.delay(println("Loading database..."))
      _ <- loadDatabase(new File("example/world.sql"))
      s <- speakerQuery("English", 10)
      _ <- s.traverseU(a => C.delay(println(a)))
    } yield "Ok"

  def loadDatabase(f: File): C.ConnectionIO[Unit] =
    for {
      ps <- C.prepareStatement("RUNSCRIPT FROM ? CHARSET 'UTF-8'")
      _  <- C.lift(ps, (PS.setString(1, f.getName) >> PS.execute) ensuring PS.close)
    } yield ()

  def speakerQuery(s: String, p: Double): C.ConnectionIO[List[CountryCode]] =
    for {
      ps <- C.prepareStatement("SELECT COUNTRYCODE FROM COUNTRYLANGUAGE WHERE LANGUAGE = ? AND PERCENTAGE > ?")
      l  <- C.lift(ps, speakerPS(s, p) ensuring PS.close)
    } yield l

  def speakerPS(s: String, p: Double): PS.PreparedStatementIO[List[CountryCode]] =
    for {
      _  <- PS.setString(1, s)
      _  <- PS.setDouble(2, p)
      rs <- PS.executeQuery
      l  <- PS.lift(rs, unroll(RS.getString(1).map(CountryCode(_))) ensuring RS.close)
    } yield l

  def unroll[A](a: RS.ResultSetIO[A]): RS.ResultSetIO[List[A]] = {
    def unroll0(as: List[A]): RS.ResultSetIO[List[A]] = 
      RS.next >>= {
        case false => as.pure[RS.ResultSetIO]
        case true  => a >>= { a => unroll0(a :: as) }
      }
    unroll0(Nil).map(_.reverse)
  }

}
