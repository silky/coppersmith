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

package commbank.coppersmith

import org.scalacheck.Prop.forAll

import org.specs2._

import au.com.cba.omnia.maestro.api.Maestro.Fields

import Feature._, Value._
import Metadata.ValueType._

import Arbitraries._

import commbank.coppersmith.test.thrift.Customer

/* More of an integration test based on a semi-realistic example. Individual feature components
 * are tested in PivotFeatureSpec that follows.
 */
object PivotFeatureSetSpec extends Specification with ScalaCheck { def is = s2"""
  PivotFeatureSet - Test an example set of features based on pivoting a record
  ===========
  An example feature set
    must generate expected metadata       $generateMetadata
    must generate expected feature values $generateFeatureValues
"""

  import Type._

  object CustomerFeatureSet extends PivotFeatureSet[Customer] {
    val namespace = "test.namespace"

    def entity(c: Customer) = c.id
    override def time(c: Customer, ctx: FeatureContext)   = c.time

    val nameF:   Feature[Customer, Str]           = pivot(Fields[Customer].Name,   "Customer name",   Nominal)
    val age:    Feature[Customer, Integral]      = pivot(Fields[Customer].Age,    "Customer age",    Nominal,
      Some(MinMaxRange(0, 130)))
    val height: Feature[Customer, FloatingPoint] = pivot(Fields[Customer].Height, "Customer height", Continuous,
      Some(MinMaxRange(40.0, 300.0)))
    val credit: Feature[Customer, FloatingPoint] = pivot(Fields[Customer].Credit, "Customer credit", Continuous)

    def features = List(nameF, age, height, credit)
  }

  def generateMetadata = {
    val metadata = CustomerFeatureSet.metadata
    import CustomerFeatureSet.namespace
    val fields = Fields[Customer]

    metadata must_== List(
      Metadata[Customer, Str]          (namespace, fields.Name.name,   "Customer name",   Nominal),
      Metadata[Customer, Integral]     (namespace, fields.Age.name,    "Customer age",    Nominal,
        Some(MinMaxRange[Integral](0, 130))),
      Metadata[Customer, FloatingPoint](namespace, fields.Height.name, "Customer height", Continuous,
        Some(MinMaxRange[FloatingPoint](40.0, 300.0))),
      Metadata[Customer, FloatingPoint](namespace, fields.Credit.name, "Customer credit", Continuous)
    )
  }

  def generateFeatureValues = forAll { (c: Customer) => {
    val featureValues = CustomerFeatureSet.generate(c)

    featureValues must_== List(
      FeatureValue[Str]          (c.id, CustomerFeatureSet.nameF.metadata.name,   c.name),
      FeatureValue[Integral]     (c.id, CustomerFeatureSet.age.metadata.name,    c.age),
      FeatureValue[FloatingPoint](c.id, CustomerFeatureSet.height.metadata.name, c.height),
      FeatureValue[FloatingPoint](c.id, CustomerFeatureSet.credit.metadata.name, c.credit)
    )
  }}
}

object PivotFeatureSpec extends Specification with ScalaCheck { def is = s2"""
  Pivot Features - Test individual pivot feature components
  ===========
  Creating pivot feature metadata
    must pass namespace through         $metadataNamespace
    must pass description through       $metadataDescription
    must use field name as feature name $metadataName
    must pass feature type through      $metadataFeatureType
    must derive value type from field   $metadataValueType

  Generating pivot feature values
    must use specified id as entity      $valueEntity
    must use field name as name          $valueName
    must use field's value as value      $valueValue
"""

  def pivot(
    ns:   Namespace,
    desc: Description,
    ft:   Type,
    e:    Customer => EntityId,
    rfp:  RangeFieldPair
  ) = {
    // Work around fact that Patterns.pivot requires field's value type,
    // which we don't get from fields arbitrary
    rfp match {
      case StrRangeFieldPair(r, f) =>
        Patterns.pivot[Customer, Str, String](ns, ft, e, f, desc, r)
      case IntegralRangeFieldPair(r, f) =>
        Patterns.pivot[Customer, Integral, Int](ns, ft, e, f, desc, r)
      case FloatingPointRangeFieldPair(r, f) =>
        Patterns.pivot[Customer, FloatingPoint, Double](ns, ft, e, f, desc, r)
    }
  }

  def metadataNamespace = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)
      feature.metadata.namespace must_== namespace
    }
  }

  def metadataName = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)
      feature.metadata.name must_== rfp.field.name
    }
  }

  def metadataDescription = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)
      feature.metadata.description must_== desc
    }
  }

  def metadataFeatureType = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)
      feature.metadata.featureType must_== fType
    }
  }

  def metadataValueType = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)

      val expectedValueType = rfp match {
        case _: StrRangeFieldPair           => StringType
        case _: IntegralRangeFieldPair      => IntegralType
        case _: FloatingPointRangeFieldPair => FloatingPointType
      }
      feature.metadata.valueType must_== expectedValueType
    }
  }

  def valueEntity = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair, c: Customer) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)
      feature.generate(c) must beSome.like { case v => v.entity must_== c.id }
    }
  }

  def valueName = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair, c: Customer) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)
      feature.generate(c) must beSome.like { case v => v.name must_== rfp.field.name }
    }
  }

  def valueValue = forAll {
    (namespace: Namespace, desc: Description, fType: Type, rfp: RangeFieldPair, c: Customer) => {
      val feature = pivot(namespace, desc, fType, _.id, rfp)

      val expectedValue = rfp match {
        case _: StrRangeFieldPair           => Str(Option(c.name))
        case _: IntegralRangeFieldPair      => Integral(Option(c.age))
        case _: FloatingPointRangeFieldPair => FloatingPoint(Option(c.height))
      }
      feature.generate(c) must beSome.like { case v => v.value must_== expectedValue }
    }
  }
}
