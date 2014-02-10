package doobie
package hi
package co

import java.sql
import dbc.op.ConnectionOps
import scalaz._
import Scalaz._
import scalaz.effect.{IO, MonadIO}
import scalaz.effect.kleisliEffect._
import scalaz.syntax.effect.monadCatchIO._
import scalaz.stream._

trait ConnectionCombinators 
  extends ConnectionOps
  with ProcessPrimitives[sql.Connection] {

  def process[P: Comp, A: Comp](sql: String, param: P): Process[Action, A] = {

    def acquire: Action[java.sql.ResultSet] = 
      push("acqiure") {
        for {
          ps <- primitive("prepareStatement", _.prepareStatement(sql))
          _  <- gosub0(ps.point[Action], preparedstatement.set1[P](param))
          rs <- gosub0(ps.point[Action], preparedstatement.primitive("executeQuery", _.executeQuery))
        } yield rs
      }

    resource[java.sql.ResultSet, A](
      acquire)(rs =>
      gosub0(rs.point[Action], resultset.close, "release"))(rs =>
      gosub0(rs.point[Action], resultset.getNext[A], "step"))

  } 


  def process0[A: Comp](sql: String): Process[Action, A] = {

    def acquire: Action[java.sql.ResultSet] = 
      push("acqiure") {
        for {
          ps <- primitive("prepareStatement", _.prepareStatement(sql))
          rs <- gosub0(ps.point[Action], preparedstatement.primitive("executeQuery", _.executeQuery))
        } yield rs
      }

    resource[java.sql.ResultSet, A](
      acquire)(rs =>
      gosub0(rs.point[Action], resultset.close, "release"))(rs =>
      gosub0(rs.point[Action], resultset.getNext[A], "step"))

  } 




}

