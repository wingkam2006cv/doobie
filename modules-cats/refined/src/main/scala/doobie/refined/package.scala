package doobie

import scala.reflect.runtime.universe.TypeTag

import doobie.imports._
import doobie.util.invariant._

import eu.timepit.refined.api.{ RefType, Validate }

package object refined {

  implicit def refinedMeta[T, P, F[_,_]](
    implicit metaT:    Meta[T],
             validate: Validate[T, P],
             refType:  RefType[F],
             manifest: TypeTag[F[T,P]]
  ): Meta[F[T, P]] =
    metaT.xmap[F[T,P]](
      refineType[T,P,F],
      unwrapRefinedType[T,P,F]
    )

  implicit def refinedComposite[T, P, F[_,_]](
    implicit compositeT: Composite[T],
             validate:   Validate[T, P],
             refType:    RefType[F],
             manifest:   TypeTag[F[T,P]]
  ): Composite[F[T,P]] =
    compositeT.imap[F[T,P]](refineType[T,P,F])(unwrapRefinedType[T,P,F])

  private def refineType[T,P,F[_,_]](t: T)(
    implicit refType:  RefType[F],
             validate: Validate[T, P],
             manifest: TypeTag[F[T,P]]
  ): F[T,P] =
    rightOrException[F[T,P]](refType.refine[P](t)(validate))

  private def unwrapRefinedType[T,P,F[_,_]](ftp: F[T,P])(
    implicit refType: RefType[F]
  ): T =
    refType.unwrap(ftp)

  private def rightOrException[T](either: Either[String, T])(
    implicit manifest: TypeTag[T]
  ): T =
    either match {
      case Left(err) => throw new SecondaryValidationFailed[T](err)(manifest)
      case Right(t) => t
    }

}
