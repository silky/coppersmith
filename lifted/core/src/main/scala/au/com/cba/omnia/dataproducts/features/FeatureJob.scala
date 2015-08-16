package au.com.cba.omnia.dataproducts.features

import com.twitter.scalding.{Config, Execution}
import com.twitter.scalding.typed._

import au.com.cba.omnia.maestro.api._

import au.com.cba.omnia.etl.util.SimpleMaestroJob

import Feature.{EntityId, Time}

trait FeatureJobConfig[S] {
  def featureSource: FeatureSource[S]
  def featureSink:   FeatureSink
}

abstract class SimpleFeatureJob extends SimpleMaestroJob {
  def generate[S](cfg:      Config => FeatureJobConfig[S],
                  features: FeatureSet[S]): Execution[JobStatus] =
    generate[S](cfg, generateOneToMany(features)_)

  def generate[S](cfg:      Config => FeatureJobConfig[S],
                  features: AggregationFeatureSet[S]): Execution[JobStatus] =
    generate[S](cfg, generateAggregate(features)_)

  def generate[S](
    cfg:      Config => FeatureJobConfig[S],
    transform: TypedPipe[S] => TypedPipe[FeatureValue]
  ): Execution[JobStatus] = {
    for {
      conf     <- Execution.getConfig.map(cfg)
      source    = conf.featureSource
      input    <- source.load(conf)
      values    = transform(input)
      _        <- conf.featureSink.write(values, conf)
    } yield JobFinished
  }

  private def generateOneToMany[S](features: FeatureSet[S])
                                  (input: TypedPipe[S]): TypedPipe[FeatureValue] =
    input.flatMap(features.generate(_))

  // FIXME: Should be able to take advantage of shapless' tuple support in combination with
  // Aggregator.join in order to run the aggregators in one pass over the input
  private def generateAggregate[S](features: AggregationFeatureSet[S])
                                  (input: TypedPipe[S]): TypedPipe[FeatureValue] = {

    val grouped: Grouped[(EntityId, Time), S] = input.groupBy(s => (features.entity(s), features.time(s)))

    features.aggregationFeatures.map(feature => {
      val name = feature.name
      grouped.aggregate(feature.aggregator).toTypedPipe.map { case ((e, t), v) =>
        FeatureValue(e, name, v, t)
      }
    }).foldLeft(TypedPipe.from(List[FeatureValue]()))(_.++(_))
  }
}
