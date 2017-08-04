package doobie.util

import scala.collection.generic.CanBuildFrom
import scala.Predef.longWrapper
import scala.concurrent.duration.{ FiniteDuration, NANOSECONDS }

import doobie.free.connection.ConnectionIO
import doobie.free.resultset.ResultSetIO
import doobie.free.preparedstatement.{ PreparedStatementIO, CatchablePreparedStatementIO }
import doobie.hi.{ connection => HC }
import doobie.hi.{ preparedstatement => HPS }
import doobie.hi.{ resultset => HRS }
import doobie.free.{ preparedstatement => FPS }
import doobie.free.{ resultset => FRS }

import doobie.util.composite.Composite
import doobie.util.analysis.Analysis
import doobie.util.log._
import doobie.util.pos.Pos
import doobie.util.fragment.Fragment

import doobie.syntax.process._
import doobie.syntax.catchable._
import doobie.syntax.catchable.ToDoobieCatchableOps._

import java.sql.ResultSet

import scalaz.{ Catchable, MonadPlus, Profunctor, Contravariant, Functor, NonEmptyList, -\/, \/- }
import scalaz.stream.Process
import scalaz.syntax.monad._
import scalaz.syntax.catchable._

/** Module defining queries parameterized by input and output types. */
object query {

  val DefaultChunkSize = 512

  /**
   * A query parameterized by some input type `A` yielding values of type `B`. We define here the
   * core operations that are needed. Additional operations are provided on `[[Query0]]` which is the
   * residual query after applying an `A`. This is the type constructed by the `sql` interpolator.
   */
  trait Query[A, B] { outer =>

    // jiggery pokery to support CBF; we're doing the coyoneda trick on B to avoid a Functor
    // constraint on the `F` parameter in `to`, and it's just easier to do the contravariant coyo
    // trick on A while we're at it.
    protected type I
    protected type O
    protected val ai: A => I
    protected val ob: O => B
    protected implicit val ic: Composite[I]
    protected implicit val oc: Composite[O]

    // LogHandler is protected for now.
    protected val logHandler: LogHandler

    private val now: PreparedStatementIO[Long] = FPS.delay(System.nanoTime)
    private def fail[T](t: Throwable): PreparedStatementIO[T] = FPS.delay(throw t)

    // Equivalent to HPS.executeQuery(k) but with logging
    private def executeQuery[T](a: A, k: ResultSetIO[T]): PreparedStatementIO[T] = {
      // N.B. the .attempt syntax isn't working in cats. unclear why
      val c = Predef.implicitly[Catchable[PreparedStatementIO]]
      val args = ic.toList(ai(a))
      def diff(a: Long, b: Long) = FiniteDuration((a - b).abs, NANOSECONDS)
      def log(e: LogEvent) = FPS.delay(logHandler.unsafeRun(e))
      for {
        t0 <- now
        er <- c.attempt(FPS.executeQuery)
        t1 <- now
        rs <- er match {
                case -\/(e) => log(ExecFailure(sql, args, diff(t1, t0), e)) *> fail[ResultSet](e)
                case \/-(a) => a.pure[PreparedStatementIO]
              }
        et <- c.attempt(FPS.lift(rs, k.ensuring(FRS.close)))
        t2 <- now
        t  <- et match {
                case -\/(e) => log(ProcessingFailure(sql, args, diff(t1, t0), diff(t2, t1), e)) *> fail(e)
                case \/-(a) => a.pure[PreparedStatementIO]
              }
        _  <- log(Success(sql, args, diff(t1, t0), diff(t2, t1)))
      } yield t
    }

    /**
     * The SQL string.
     * @group Diagnostics
     */
    def sql: String

    /**
     * An optional `[[Pos]]` indicating the source location where this `[[Query]]` was
     * constructed. This is used only for diagnostic purposes.
     * @group Diagnostics
     */
    def pos: Option[Pos]

    /** Turn this `Query` into a `Fragment`, given an argument. */
    def toFragment(a: A): Fragment =
      Fragment(sql, ai(a), pos)

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
     * Apply the argument `a` to construct a `Process` with the given chunking factor, with
     * effect type  `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding elements of
     * type `B`.
     * @group Results
     */
    def processWithChunkSize(a: A, chunkSize: Int): Process[ConnectionIO, B] =
      HC.process[O](sql, HPS.set(ai(a)), chunkSize).map(ob)

    /**
     * FS2 Friendly Alias for processWithChunkSize.
     * @group Results
     */
    def streamWithChunkSize(a: A, chunkSize: Int): Process[ConnectionIO, B] =
      processWithChunkSize(a, chunkSize)

    /**
     * Apply the argument `a` to construct a `Process` with `DefaultChunkSize`, with
     * effect type  `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding elements of
     * type `B`.
     * @group Results
     */
    def process(a: A): Process[ConnectionIO, B] =
      processWithChunkSize(a, DefaultChunkSize)

    /**
     * FS2 Friendly Alias for process.
     * @group Results
     */
    def stream(a: A): Process[ConnectionIO, B] =
      process(a)

    /**
     * Apply the argument `a` to construct a program in
     *`[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]` accumulated
     * via the provided `CanBuildFrom`. This is the fastest way to accumulate a collection.
     * @group Results
     */
    def to[F[_]](a: A)(implicit cbf: CanBuildFrom[Nothing, B, F[B]]): ConnectionIO[F[B]] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> executeQuery(a, HRS.buildMap[F,O,B](ob)))

    /**
     * Apply the argument `a` to construct a program in
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]` accumulated
     * via `MonadPlus` append. This method is more general but less efficient than `to`.
     * @group Results
     */
    def accumulate[F[_]: MonadPlus](a: A): ConnectionIO[F[B]] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> executeQuery(a, HRS.accumulate[F, O].map(_.map(ob))))

    /**
     * Apply the argument `a` to construct a program in
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding a unique `B` and
     * raising an exception if the resultset does not have exactly one row. See also `option`.
     * @group Results
     */
    def unique(a: A): ConnectionIO[B] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> executeQuery(a, HRS.getUnique[O])).map(ob)

    /**
     * Apply the argument `a` to construct a program in
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an optional `B` and
     * raising an exception if the resultset has more than one row. See also `unique`.
     * @group Results
     */
    def option(a: A): ConnectionIO[Option[B]] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> executeQuery(a, HRS.getOption[O])).map(_.map(ob))

    /**
      * Apply the argument `a` to construct a program in
      * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `NonEmptyList[B]` and
      * raising an exception if the resultset does not have at least one row. See also `unique`.
      * @group Results
      */
    def nel(a: A): ConnectionIO[NonEmptyList[B]] =
      HC.prepareStatement(sql)(HPS.set(ai(a)) *> executeQuery(a, HRS.nel[O])).map(_.map(ob))
    
    /**
     * Convenience method; equivalent to `to[List]`
     * @group Results
     */
    def list(a: A): ConnectionIO[List[B]] = to[List](a)

    /**
     * Convenience method; equivalent to `to[Vector]`
     * @group Results
     */
    def vector(a: A): ConnectionIO[Vector[B]] = to[Vector](a)

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
        def pos = outer.pos
        val logHandler = outer.logHandler
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
        def pos = outer.pos
        val logHandler = outer.logHandler
      }

    /**
     * Apply an argument, yielding a residual `[[Query0]]`.
     * @group Transformations
     */
    def toQuery0(a: A): Query0[B] =
      new Query0[B] {
        def sql = outer.sql
        def pos = outer.pos
        def toFragment = outer.toFragment(a)
        def analysis = outer.analysis
        def outputAnalysis = outer.outputAnalysis
        def processWithChunkSize(n: Int) = outer.processWithChunkSize(a, n)
        def accumulate[F[_]: MonadPlus] = outer.accumulate[F](a)
        def to[F[_]](implicit cbf: CanBuildFrom[Nothing, B, F[B]]) = outer.to[F](a)
        def unique = outer.unique(a)
        def option = outer.option(a)
        def nel = outer.nel(a)
        def map[C](f: B => C): Query0[C] = outer.map(f).toQuery0(a)
      }

  }

  object Query {

    /**
     * Construct a `Query` with the given SQL string, an optional `Pos` for diagnostic
     * purposes, and composite type arguments for input and output types. Note that the most common
     * way to construct a `Query` is via the `sql` interpolator.
     * @group Constructors
     */
    def apply[A, B](sql0: String, pos0: Option[Pos] = None, logHandler0: LogHandler = LogHandler.nop)(implicit A: Composite[A], B: Composite[B]): Query[A, B] =
      new Query[A, B] {
        type I = A
        type O = B
        val ai: A => I = a => a
        val ob: O => B = o => o
        implicit val ic: Composite[I] = A
        implicit val oc: Composite[O] = B
        val sql = sql0
        val pos = pos0
        val logHandler = logHandler0
      }

    /** @group Typeclass Instances */
    implicit val queryProfunctor: Profunctor[Query] =
      new Profunctor[Query] {
        def mapfst[A, B, C](fab: Query[A,B])(f: C => A) = fab contramap f
        def mapsnd[A, B, C](fab: Query[A,B])(f: B => C) = fab map f
      }

    /** @group Typeclass Instances */
    implicit def queryCovariant[A]: Functor[Query[A, ?]] =
      queryProfunctor.covariantInstance[A]

    /** @group Typeclass Instances */
    implicit def queryContravariant[B]: Contravariant[Query[?, B]] =
      queryProfunctor.contravariantInstance[B]

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
     * An optional `Pos` indicating the source location where this `Query` was
     * constructed. This is used only for diagnostic purposes.
     * @group Diagnostics
     */
    def pos: Option[Pos]

    /** Turn this `Query0` into a `Fragment`. */
    def toFragment: Fragment

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
     * `Process` with default chunk factor, with effect type
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding  elements of type `B`.
     * @group Results
     */
    def process: Process[ConnectionIO, B] =
      processWithChunkSize(DefaultChunkSize)

    /**
     * FS2 Friendly Alias for process.
     * @group Results
     */
    def stream : Process[ConnectionIO, B] =
      process

    /**
     * `Process` with given chunk factor, with effect type
     * `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding  elements of type `B`.
     * @group Results
     */
    def processWithChunkSize(n: Int): Process[ConnectionIO, B]

    /**
     * FS2 Friendly Alias for processWithChunkSize.
     * @group Results
     */
    def streamWithChunkSize(n: Int): Process[ConnectionIO, B] =
      processWithChunkSize(n)

    /**
     * Program in `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]`
     * accumulated via the provided `CanBuildFrom`. This is the fastest way to accumulate a
     * collection.
     * @group Results
     */
    def to[F[_]](implicit cbf: CanBuildFrom[Nothing, B, F[B]]): ConnectionIO[F[B]]

    /**
     * Program in `[[doobie.free.connection.ConnectionIO ConnectionIO]]` yielding an `F[B]`
     * accumulated via `MonadPlus` append. This method is more general but less efficient than `to`.
     * @group Results
     */
    def accumulate[F[_]: MonadPlus]: ConnectionIO[F[B]]

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

    /**
     * Convenience method; equivalent to `process.sink(f)`
     * @group Results
     */
    def sink(f: B => ConnectionIO[Unit]): ConnectionIO[Unit] = process.sink(f)

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

    /**
     * Construct a `Query` with the given SQL string, an optional `Pos` for diagnostic
     * purposes, with no parameters. Note that the most common way to construct a `Query` is via the
     * `sql`interpolator.
     * @group Constructors
     */
     def apply[A: Composite](sql: String, pos: Option[Pos] = None, logHandler: LogHandler = LogHandler.nop): Query0[A] =
       Query[Unit, A](sql, pos, logHandler).toQuery0(())

    /** @group Typeclass Instances */
    implicit val queryFunctor: Functor[Query0] =
      new Functor[Query0] {
        def map[A, B](fa: Query0[A])(f: A => B) = fa map f
      }

  }

}
