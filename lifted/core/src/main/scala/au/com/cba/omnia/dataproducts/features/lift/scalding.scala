package au.com.cba.omnia.dataproducts.features.lift

import au.com.cba.omnia.dataproducts.features.Feature.Value
import au.com.cba.omnia.dataproducts.features.Join.Joined
import au.com.cba.omnia.dataproducts.features._
import com.twitter.scalding._

trait ScaldingLift extends Lift[TypedPipe] with Materialise[TypedPipe, TypedSink, Execution] {

  def lift[S,V <: Value](f:Feature[S,V])(s: TypedPipe[S]): TypedPipe[FeatureValue] = {
    s.flatMap(s => f.generate(s))
  }

  def lift[S](fs: FeatureSet[S])(s: TypedPipe[S]): TypedPipe[FeatureValue] = {
    s.flatMap(s => fs.generate(s))
  }


  def liftJoin[A, B, J : Ordering](joined: Joined[A, B, J])(a:TypedPipe[A], b: TypedPipe[B]): TypedPipe[(A, B)] = {
    a.groupBy(joined.left).join(b.groupBy(joined.right)).values
  }

  def materialiseJoinFeature[A, B, J : Ordering, V <: Value]
    (joined: Joined[A, B, J], feature: Feature[(A,B),V])
    (leftSrc:TypedPipe[A], rightSrc:TypedPipe[B], sink: TypedSink[FeatureValue]) =
      materialise[(A,B), V](feature)(liftJoin(joined)(leftSrc, rightSrc), sink)

  def materialise[S,V <: Value](f:Feature[S,V])(src:TypedPipe[S], sink: TypedSink[FeatureValue]): Execution[Unit] = {
    val pipe = lift(f)(src)
    pipe.writeExecution(sink)
  }

  def materialise[S](featureSet: FeatureSet[S])(src:TypedPipe[S], sink: TypedSink[FeatureValue]): Execution[Unit] = {
    val pipe = lift(featureSet)(src)
    pipe.writeExecution(sink)
  }
}

object scalding extends ScaldingLift
