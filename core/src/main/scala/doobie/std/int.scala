package doobie.std

import doobie.free.preparedstatement.setInt
import doobie.free.resultset.getInt
import doobie.util.atom.Atom
import doobie.enum.jdbctype

object int {

  implicit val IntAtom: Atom[Int] = 
    Atom.atom(jdbctype.Integer, setInt, getInt)

}