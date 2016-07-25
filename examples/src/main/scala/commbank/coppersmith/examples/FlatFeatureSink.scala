//
// Copyright 2016 Commonwealth Bank of Australia
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//        http://www.apache.org/licenses/LICENSE-2.0
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//

package commbank.coppersmith.examples.userguide

import org.apache.hadoop.fs.Path

import com.twitter.scalding.typed.TypedPipe
import com.twitter.scalding.TypedTsv

import commbank.coppersmith.Feature.{FeatureTime, Value}
import commbank.coppersmith.Feature.Value._
import commbank.coppersmith.FeatureValue
import commbank.coppersmith.scalding.FeatureSink, FeatureSink.WriteResult

case class FlatFeatureSink(output: String) extends FeatureSink {
  def path = new Path(output)
  override def write(features: TypedPipe[(FeatureValue[Value], FeatureTime)]): WriteResult = {

    val featurePipe = features.map {
      case (fv, t) =>
        val featureValue = (fv.value match {
          case Integral(v)      => v.map(_.toString)
          case Decimal(v)       => v.map(_.toString)
          case FloatingPoint(v) => v.map(_.toString)
          case Str(v)           => v
          case Bool(v)          => v.map(_.toString)
          case Date(v)          => v.map(_.toString)
          case Time(v)          => v.map(_.toString)
        }).getOrElse("")
        s"${fv.entity}|${fv.name}|${featureValue}"
    }
    featurePipe.writeExecution(TypedTsv[String](output)).map(_ => Right(Set(path)))
  }
}
