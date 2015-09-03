package commbank.coppersmith.scalding.lift

import com.twitter.scalding._

import commbank.coppersmith._, Feature.Value, Join._
import commbank.coppersmith.scalding.ScaldingConfiguredFeatureSource

trait ScaldingLift extends Lift[TypedPipe] {

  def lift[S, V <: Value](f:Feature[S,V])(s: TypedPipe[S]): TypedPipe[FeatureValue[V]] = {
    s.flatMap(s => f.generate(s))
  }

  def lift[S](fs: FeatureSet[S])(s: TypedPipe[S]): TypedPipe[FeatureValue[_]] = {
    s.flatMap(s => fs.generate(s))
  }


  def liftJoin[A, B, J : Ordering](joined: Joined[A, B, J, Inner])
                                  (a:TypedPipe[A], b: TypedPipe[B]): TypedPipe[(A, B)] =
    a.groupBy(joined.left).join(b.groupBy(joined.right)).values


  def liftLeftJoin[A, B, J : Ordering](joined: Joined[A, B, J, LeftOuter])
                                  (a:TypedPipe[A], b: TypedPipe[B]): TypedPipe[(A, Option[B])] =
    a.groupBy(joined.left).leftJoin(b.groupBy(joined.right)).values

  def liftBinder[S, U, B <: SourceBinder[S, U, TypedPipe]](
    underlying: U,
    binder: B,
    filter: Option[S => Boolean]
  ) = ScaldingConfiguredFeatureSource(underlying, binder, filter)
}

object scalding extends ScaldingLift