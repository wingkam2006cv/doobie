package doobie.util

import scala.collection.generic.CanBuildFrom

import doobie.free.connection.ConnectionIO
import doobie.hi.{ connection => HC }
import doobie.hi.{ preparedstatement => HPS }
import doobie.hi.{ resultset => HRS }

import doobie.util.composite.Composite
import doobie.util.analysis.Analysis

#+scalaz
import doobie.syntax.process._

import scalaz.{ MonadPlus, Profunctor, Contravariant, Functor, NonEmptyList }
import scalaz.stream.Process
import scalaz.syntax.monad._
#-scalaz
#+cats
import cats.implicits._
import cats.Functor
import cats.functor.{ Contravariant, Profunctor }
import cats.data.NonEmptyList
#-cats
#+fs2
import fs2.{ Stream => Process }
#-fs2

/** Module defining queries parameterized by input and output types. */
object query {

  /** 
   * A query parameterized by some input type `A` yielding values of type `B`. We define here the
   * core operations that are needed. Additional operations are provided on `[[Query0]]` which is the 
   * residual query after applying an `A`. This is the type constructed by the `sql` interpolator.
   */
  trait Query[A, B] { outer =>

    // jiggery pokery to support CBF; we're doing the coyoneda trick on B to to avoid a Functor
    // constraint on the `F` parameter in `to`, and it's just easier to do the contravariant coyo 
    // trick on A while we're at it.
    protected type I
    protected type O
    protected val ai: A => I
    protected val ob: O => B
    protected implicit val ic: Composite[I]
    protected implicit val oc: Composite[O]

    /** 
     * The SQL string. 
     * @group Diagnostics
     */
    def sql: String
    
    /** 
     * An optional `[[StackTraceElement]]` indicating the source location where this `[[Query]]` was 
     * constructed. This is used only for diagnostic purposes. 
     * @group Diagnostics
     */
    def stackFrame: Option[StackTraceElement]

    /**
     * Program to construct an analysis of this query's SQL statement and asserted parameter and
     * column types.
     * @group Diagnostics
     */
    def analysis: ConnectionIO[Analysis] =
      HC.prepareQueryAnalysis[I, O](sql)
    /**
     * Program to construct an analysis of this query's SQL statement and result set column types.
     * @group Diagnostics
     */
    def outputAnalysis: ConnectionIO[Analysis] =
      HC.prepareQueryAnalysis0[O](sql)

    /**
     * Apply the argument `a` to construct a `Process` with effect type 
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding elements of type `B`.
     * @group Results
     */
    def process(a: A): Process[ConnectionIO, B] = 
      HC.process[O](sql, HPS.set(ai(a))).map(ob)

    /**
     * Apply the argument `a` to construct a program in 
     *`[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]` accumulated
     * via the provided `CanBuildFrom`. This is the fastest way to accumulate a collection.
     * @group Results
     */
    def to[F[_]](a: A)(implicit cbf: CanBuildFrom[Nothing, B, F[B]]): ConnectionIO[F[B]] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> HPS.executeQuery(HRS.buildMap[F,O,B](ob)))

#+scalaz
    /**
     * Apply the argument `a` to construct a program in 
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]` accumulated
     * via `MonadPlus` append. This method is more general but less efficient than `to`.
     * @group Results
     */
    def accumulate[F[_]: MonadPlus](a: A): ConnectionIO[F[B]] = 
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> HPS.executeQuery(HRS.accumulate[F, O].map(_.map(ob))))
#-scalaz

    /**
     * Apply the argument `a` to construct a program in 
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding a unique `B` and
     * raising an exception if the resultset does not have exactly one row. See also `option`.
     * @group Results
     */
    def unique(a: A): ConnectionIO[B] = 
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> HPS.executeQuery(HRS.getUnique[O])).map(ob)

    /**
     * Apply the argument `a` to construct a program in
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an optional `B` and
     * raising an exception if the resultset has more than one row. See also `unique`.
     * @group Results
     */
    def option(a: A): ConnectionIO[Option[B]] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> HPS.executeQuery(HRS.getOption[O])).map(_.map(ob))

    /**
      * Apply the argument `a` to construct a program in
      * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `NonEmptyList[B]` and
      * raising an exception if the resultset does not have at least one row. See also `unique`.
      * @group Results
      */
    def nel(a: A): ConnectionIO[NonEmptyList[B]] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> HPS.executeQuery(HRS.nel[O])).map(_.map(ob))

    /** @group Transformations */
    def map[C](f: B => C): Query[A, C] =
      new Query[A, C] {
        type I = outer.I
        type O = outer.O
        val ai = outer.ai
        val ob = outer.ob andThen f
        val ic: Composite[I] = outer.ic 
        val oc: Composite[O] = outer.oc
        def sql = outer.sql
        def stackFrame = outer.stackFrame
      }

    /** @group Transformations */
    def contramap[C](f: C => A): Query[C, B] =
      new Query[C, B] {
        type I = outer.I
        type O = outer.O
        val ai = outer.ai compose f
        val ob = outer.ob
        val ic: Composite[I] = outer.ic 
        val oc: Composite[O] = outer.oc
        def sql = outer.sql
        def stackFrame = outer.stackFrame
      }

    /** 
     * Apply an argument, yielding a residual `[[Query0]]`. Note that this is the typical (and the 
     * only provided) way to construct a `[[Query0]]`.
     * @group Transformations
     */
    def toQuery0(a: A): Query0[B] =
      new Query0[B] {
        def sql = outer.sql
        def stackFrame = outer.stackFrame
        def analysis = outer.analysis
        def outputAnalysis = outer.outputAnalysis
        def process = outer.process(a)
#+scalaz
        def accumulate[F[_]: MonadPlus] = outer.accumulate[F](a)  
#-scalaz
        def to[F[_]](implicit cbf: CanBuildFrom[Nothing, B, F[B]]) = outer.to[F](a)
        def unique = outer.unique(a)
        def option = outer.option(a)
        def nel = outer.nel(a)
        def map[C](f: B => C): Query0[C] = outer.map(f).toQuery0(a)
      }

  }

  object Query {

    /**
     * Construct a `Query` with the given SQL string, an optional `StackTraceElement` for diagnostic
     * purposes, and composite type arguments for input and output types. The most common way to
     * construct a `Query` is via the `sql` interpolator.
     * @group Constructors
     */
    def apply[A, B](sql0: String, stackFrame0: Option[StackTraceElement] = None)(implicit A: Composite[A], B: Composite[B]): Query[A, B] =
      new Query[A, B] {
        type I = A
        type O = B
        val ai: A => I = a => a
        val ob: O => B = o => o
        implicit val ic: Composite[I] = A
        implicit val oc: Composite[O] = B
        val sql = sql0
        val stackFrame = stackFrame0
      }

    /** @group Typeclass Instances */
    implicit val queryProfunctor: Profunctor[Query] =
      new Profunctor[Query] {
#+scalaz
        def mapfst[A, B, C](fab: Query[A,B])(f: C => A) = fab contramap f
        def mapsnd[A, B, C](fab: Query[A,B])(f: B => C) = fab map f
#-scalaz
#+cats
        def dimap[A, B, C, D](fab: Query[A,B])(f: C => A)(g: B => D): Query[C,D] =
          fab.contramap(f).map(g)
#-cats
      }

    /** @group Typeclass Instances */
    implicit def queryCovariant[A]: Functor[Query[A, ?]] =
#+scalaz
      queryProfunctor.covariantInstance[A]
#-scalaz
#+cats
      new Functor[Query[A, ?]] {
        def map[B, C](fa: Query[A, B])(f: B => C): Query[A, C] =
          fa.map(f)
      }
#-cats

    /** @group Typeclass Instances */
    implicit def queryContravariant[B]: Contravariant[Query[?, B]] =
#+scalaz
      queryProfunctor.contravariantInstance[B]
#-scalaz
#+cats
      new Contravariant[Query[?, B]] {
        def contramap[A, C](fa: Query[A, B])(f: C => A): Query[C, B] =
          fa.contramap(f)
      }
#-cats

  }

  /** 
   * An abstract query closed over its input arguments and yielding values of type `B`, without a
   * specified disposition. Methods provided on `[[Query0]]` allow the query to be interpreted as a
   * stream or program in `CollectionIO`.
   */
  trait Query0[B] { outer =>

    /** 
     * The SQL string. 
     * @group Diagnostics
     */
    def sql: String  

    /** 
     * An optional `StackTraceElement` indicating the source location where this `Query` was 
     * constructed. This is used only for diagnostic purposes. 
     * @group Diagnostics
     */
    def stackFrame: Option[StackTraceElement]

    /**
     * Program to construct an analysis of this query's SQL statement and asserted parameter and
     * column types.
     * @group Diagnostics
     */
    def analysis: ConnectionIO[Analysis] 

    /** 
     * Program to construct an analysis of this query's SQL statement and result set column types.
     * @group Diagnostics
     */
    def outputAnalysis: ConnectionIO[Analysis]

    /**
     * `Process` with effect type `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding 
     * elements of type `B`. 
     * @group Results
     */
    def process: Process[ConnectionIO, B]

    /**
     * Program in `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]` 
     * accumulated via the provided `CanBuildFrom`. This is the fastest way to accumulate a 
     * collection.
     * @group Results
     */
    def to[F[_]](implicit cbf: CanBuildFrom[Nothing, B, F[B]]): ConnectionIO[F[B]]

#+scalaz
    /**
     * Program in `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]` 
     * accumulated via `MonadPlus` append. This method is more general but less efficient than `to`.
     * @group Results
     */
    def accumulate[F[_]: MonadPlus]: ConnectionIO[F[B]]
#-scalaz

    /**
     * Program in `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding a unique `B` and 
     * raising an exception if the resultset does not have exactly one row. See also `option`.
     * @group Results
     */
    def unique: ConnectionIO[B]

    /**
     * Program in `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an optional `B`
     * and raising an exception if the resultset has more than one row. See also `unique`.
     * @group Results
     */
    def option: ConnectionIO[Option[B]]

    /**
      * Program in `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding a `NonEmptyList[B]`
      * and raising an exception if the resultset does not have at least one row. See also `unique`.
      * @group Results
      */
    def nel: ConnectionIO[NonEmptyList[B]]

    /** @group Transformations */
    def map[C](f: B => C): Query0[C] 

#+scalaz
    /** 
     * Convenience method; equivalent to `process.sink(f)` 
     * @group Results
     */
    def sink(f: B => ConnectionIO[Unit]): ConnectionIO[Unit] = process.sink(f)
#-scalaz

    /** 
     * Convenience method; equivalent to `to[List]` 
     * @group Results
     */
    def list: ConnectionIO[List[B]] = to[List]

    /** 
     * Convenience method; equivalent to `to[Vector]` 
     * @group Results
     */
    def vector: ConnectionIO[Vector[B]] = to[Vector]

  }

  object Query0 {

    /** @group Typeclass Instances */
    implicit val queryFunctor: Functor[Query0] =
      new Functor[Query0] {
        def map[A, B](fa: Query0[A])(f: A => B) = fa map f
      }

  }

}
