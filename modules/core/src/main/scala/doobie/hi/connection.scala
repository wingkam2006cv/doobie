package doobie.hi

import doobie.enum.holdability._
import doobie.enum.resultsettype._
import doobie.enum.resultsetconcurrency._
import doobie.enum.transactionisolation._
import doobie.enum.autogeneratedkeys.AutoGeneratedKeys
import doobie.enum.jdbctype.JdbcType

import doobie.syntax.catchable.ToDoobieCatchableOps._

import doobie.util.analysis.Analysis
import doobie.util.composite.Composite
import doobie.util.process.repeatEvalChunks

import doobie.free.{ connection => C }
import doobie.free.{ preparedstatement => PS }
import doobie.free.{ callablestatement => CS }
import doobie.free.{ resultset => RS }
import doobie.free.{ statement => S }
import doobie.free.{ databasemetadata => DMD }

import doobie.hi.{ preparedstatement => HPS }
import doobie.hi.{ resultset => HRS }

import java.sql.{ Savepoint, PreparedStatement, ResultSet }

import scala.collection.immutable.Map
import scala.collection.JavaConverters._

import cats.Foldable
import cats.implicits._
import fs2.{ Stream => Process }
import fs2.Stream.{ eval, bracket }

/**
 * Module of high-level constructors for `ConnectionIO` actions.
 * @group Modules
 */
object connection {

  /** @group Typeclass Instances */
  implicit val CatchableConnectionIO = C.CatchableConnectionIO

  /** @group Lifting */
  def delay[A](a: => A): ConnectionIO[A] =
    C.delay(a)

  private def liftProcess[A: Composite](
    chunkSize: Int,
    create: ConnectionIO[PreparedStatement],
    prep:   PreparedStatementIO[Unit],
    exec:   PreparedStatementIO[ResultSet]): Process[ConnectionIO, A] = {

    def prepared(ps: PreparedStatement): Process[ConnectionIO, PreparedStatement] =
      eval[ConnectionIO, PreparedStatement] {
        val fs = PS.setFetchSize(chunkSize)
        C.lift(ps, fs >> prep).map(_ => ps)
      }

    def unrolled(rs: ResultSet): Process[ConnectionIO, A] =
      repeatEvalChunks(C.lift(rs, resultset.getNextChunk[A](chunkSize)))

    val preparedStatement: Process[ConnectionIO, PreparedStatement] =
      bracket(create)(prepared, C.lift(_, PS.close))

    def results(ps: PreparedStatement): Process[ConnectionIO, A] =
      bracket(C.lift(ps, exec))(unrolled, C.lift(_, RS.close))

    preparedStatement.flatMap(results)

  }

  /**
   * Construct a prepared statement from the given `sql`, configure it with the given `PreparedStatementIO`
   * action, and return results via a `Process`.
   * @group Prepared Statements
   */
  def process[A: Composite](sql: String, prep: PreparedStatementIO[Unit], chunkSize: Int): Process[ConnectionIO, A] =
    liftProcess(chunkSize, C.prepareStatement(sql), prep, PS.executeQuery)

  /**
   * Construct a prepared update statement with the given return columns (and composite destination
   * type `A`) and sql source, configure it with the given `PreparedStatementIO` action, and return
   * the generated key results via a
   * `Process`.
   * @group Prepared Statements
   */
  def updateWithGeneratedKeys[A: Composite](cols: List[String])(sql: String, prep: PreparedStatementIO[Unit], chunkSize: Int): Process[ConnectionIO, A] =
    liftProcess(chunkSize, C.prepareStatement(sql, cols.toArray), prep, PS.executeUpdate >> PS.getGeneratedKeys)

  /** @group Prepared Statements */
  def updateManyWithGeneratedKeys[F[_]: Foldable, A: Composite, B: Composite](cols: List[String])(sql: String, prep: PreparedStatementIO[Unit], fa: F[A], chunkSize: Int): Process[ConnectionIO, B] =
    liftProcess[B](chunkSize, C.prepareStatement(sql, cols.toArray), prep, HPS.addBatchesAndExecute(fa) >> PS.getGeneratedKeys)

  /** @group Transaction Control */
  val commit: ConnectionIO[Unit] =
    C.commit

  /**
   * Construct an analysis for the provided `sql` query, given parameter composite type `A` and
   * resultset row composite `B`.
   */
  def prepareQueryAnalysis[A: Composite, B: Composite](sql: String): ConnectionIO[Analysis] =
    nativeTypeMap flatMap (m => prepareStatement(sql) {
      (HPS.getParameterMappings[A] |@| HPS.getColumnMappings[B]) map (Analysis(sql, m, _, _))
    })

  def prepareQueryAnalysis0[B: Composite](sql: String): ConnectionIO[Analysis] =
    nativeTypeMap flatMap (m => prepareStatement(sql) {
      HPS.getColumnMappings[B] map (cm => Analysis(sql, m, Nil, cm))
    })

  def prepareUpdateAnalysis[A: Composite](sql: String): ConnectionIO[Analysis] =
    nativeTypeMap flatMap (m => prepareStatement(sql) {
      HPS.getParameterMappings[A] map (pm => Analysis(sql, m, pm, Nil))
    })

  def prepareUpdateAnalysis0(sql: String): ConnectionIO[Analysis] =
    nativeTypeMap flatMap (m => prepareStatement(sql) {
      Analysis(sql, m, Nil, Nil).pure[PreparedStatementIO]
    })


  /** @group Statements */
  def createStatement[A](k: StatementIO[A]): ConnectionIO[A] =
    C.createStatement.flatMap(s => C.lift(s, k ensuring S.close))

  /** @group Statements */
  def createStatement[A](rst: ResultSetType, rsc: ResultSetConcurrency)(k: StatementIO[A]): ConnectionIO[A] =
    C.createStatement(rst.toInt, rsc.toInt).flatMap(s => C.lift(s, k ensuring S.close))

  /** @group Statements */
  def createStatement[A](rst: ResultSetType, rsc: ResultSetConcurrency, rsh: Holdability)(k: StatementIO[A]): ConnectionIO[A] =
    C.createStatement(rst.toInt, rsc.toInt, rsh.toInt).flatMap(s => C.lift(s, k ensuring S.close))

  /** @group Connection Properties */
  val getCatalog: ConnectionIO[String] =
    C.getCatalog

  /** @group Connection Properties */
  def getClientInfo(key: String): ConnectionIO[Option[String]] =
    C.getClientInfo(key).map(Option(_))

  /** @group Connection Properties */
  val getClientInfo: ConnectionIO[Map[String, String]] =
    C.getClientInfo.map(_.asScala.toMap)

  /** @group Connection Properties */
  val getHoldability: ConnectionIO[Holdability] =
    C.getHoldability.map(Holdability.unsafeFromInt)

  /** @group Connection Properties */
  def getMetaData[A](k: DatabaseMetaDataIO[A]): ConnectionIO[A] =
    C.getMetaData.flatMap(s => C.lift(s, k))

  /** @group Transaction Control */
  val getTransactionIsolation: ConnectionIO[TransactionIsolation] =
    C.getTransactionIsolation.map(TransactionIsolation.unsafeFromInt)

  /** @group Connection Properties */
  val isReadOnly: ConnectionIO[Boolean] =
    C.isReadOnly

  /** @group Callable Statements */
  def prepareCall[A](sql: String, rst: ResultSetType, rsc: ResultSetConcurrency)(k: CallableStatementIO[A]): ConnectionIO[A] =
    C.prepareCall(sql, rst.toInt, rsc.toInt).flatMap(s => C.lift(s, k ensuring CS.close))

  /** @group Callable Statements */
  def prepareCall[A](sql: String)(k: CallableStatementIO[A]): ConnectionIO[A] =
    C.prepareCall(sql).flatMap(s => C.lift(s, k ensuring CS.close))

  /** @group Callable Statements */
  def prepareCall[A](sql: String, rst: ResultSetType, rsc: ResultSetConcurrency, rsh: Holdability)(k: CallableStatementIO[A]): ConnectionIO[A] =
    C.prepareCall(sql, rst.toInt, rsc.toInt, rsh.toInt).flatMap(s => C.lift(s, k ensuring CS.close))

  /** @group Prepared Statements */
  def prepareStatement[A](sql: String, rst: ResultSetType, rsc: ResultSetConcurrency)(k: PreparedStatementIO[A]): ConnectionIO[A] =
    C.prepareStatement(sql, rst.toInt, rsc.toInt).flatMap(s => C.lift(s, k ensuring PS.close))

  /** @group Prepared Statements */
  def prepareStatement[A](sql: String)(k: PreparedStatementIO[A]): ConnectionIO[A] =
    C.prepareStatement(sql).flatMap(s => C.lift(s, k ensuring PS.close))

  /** @group Prepared Statements */
  def prepareStatement[A](sql: String, rst: ResultSetType, rsc: ResultSetConcurrency, rsh: Holdability)(k: PreparedStatementIO[A]): ConnectionIO[A] =
    C.prepareStatement(sql, rst.toInt, rsc.toInt, rsh.toInt).flatMap(s => C.lift(s, k ensuring PS.close))

  /** @group Prepared Statements */
  def prepareStatement[A](sql: String, agk: AutoGeneratedKeys)(k: PreparedStatementIO[A]): ConnectionIO[A] =
    C.prepareStatement(sql, agk.toInt).flatMap(s => C.lift(s, k ensuring PS.close))

  /** @group Prepared Statements */
  def prepareStatementI[A](sql: String, columnIndexes: List[Int])(k: PreparedStatementIO[A]): ConnectionIO[A] =
    C.prepareStatement(sql, columnIndexes.toArray).flatMap(s => C.lift(s, k ensuring PS.close))

  /** @group Prepared Statements */
  def prepareStatementS[A](sql: String, columnNames: List[String])(k: PreparedStatementIO[A]): ConnectionIO[A] =
    C.prepareStatement(sql, columnNames.toArray).flatMap(s => C.lift(s, k ensuring PS.close))

  /** @group Transaction Control */
  def releaseSavepoint(sp: Savepoint): ConnectionIO[Unit] =
    C.releaseSavepoint(sp)

  /** @group Transaction Control */
  def rollback(sp: Savepoint): ConnectionIO[Unit] =
    C.rollback(sp)

  /** @group Transaction Control */
  val rollback: ConnectionIO[Unit] =
    C.rollback

  /** @group Connection Properties */
  def setCatalog(catalog: String): ConnectionIO[Unit] =
    C.setCatalog(catalog)

  /** @group Connection Properties */
  def setClientInfo(key: String, value: String): ConnectionIO[Unit] =
    C.setClientInfo(key, value)

  /** @group Connection Properties */
  def setClientInfo(info: Map[String, String]): ConnectionIO[Unit] =
    C.setClientInfo {
      val ps = new java.util.Properties
      ps.putAll(info.asJava)
      ps
    }

  /** @group Connection Properties */
  def setHoldability(h: Holdability): ConnectionIO[Unit] =
    C.setHoldability(h.toInt)

  /** @group Connection Properties */
  def setReadOnly(readOnly: Boolean): ConnectionIO[Unit] =
    C.setReadOnly(readOnly)

  /** @group Transaction Control */
  val setSavepoint: ConnectionIO[Savepoint] =
    C.setSavepoint

  /** @group Transaction Control */
  def setSavepoint(name: String): ConnectionIO[Savepoint] =
    C.setSavepoint(name)

  /** @group Transaction Control */
  def setTransactionIsolation(ti: TransactionIsolation): ConnectionIO[Unit] =
    C.setTransactionIsolation(ti.toInt)

  /**
   * Compute a map from native type to closest-matching JDBC type.
   * @group MetaData
   */
  val nativeTypeMap: ConnectionIO[Map[String, JdbcType]] = {
    getMetaData(DMD.getTypeInfo.flatMap(DMD.lift(_, HRS.list[(String, JdbcType)].map(_.toMap))))
  }
}
