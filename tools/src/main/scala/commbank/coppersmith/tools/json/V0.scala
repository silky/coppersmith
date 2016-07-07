package commbank.coppersmith.tools.json

import argonaut._, Argonaut._
import scalaz._, Scalaz._

import Util.stripNullValuesFromObjects

case class MetadataJsonV0(features: List[FeatureMetadataV0]) extends MetadataJson {
  val version = 0
}

case class FeatureMetadataV0 (
  namespace: String,
  name:String,
  description: String,
  source: String,
  typesConform: Boolean,
  valueType: String,
  featureType: String,
  range: Option[RangeV0])

sealed trait RangeV0
//These are strings because we want to represent 64-bit numbers,
// some of which are not representable by JSON
case class NumericRangeV0(min: Option[String], max: Option[String]) extends RangeV0

case class SetRangeV0(elements: List[Option[String]]) extends RangeV0

object CodecsV0 {
  implicit lazy val featureMetadataV0Codec: CodecJson[FeatureMetadataV0] =
    CodecJson.derived(stripNullValuesFromObjects(EncodeJson.derive), DecodeJson.derive)

  lazy val numericRangeV0Codec = CodecJson.derive[NumericRangeV0].map(it => it : RangeV0)


  implicit lazy val rangeV0Decode: DecodeJson[RangeV0] = DecodeJson(
    c => c.focus.arrayOrObject(
      DecodeResult.fail("Either an array or JSON object expected", c.history),
      arr => arr.map((jsn: Json) => jsn.as[Option[String]]).sequenceU.map(SetRangeV0.apply),
      obj =>  jObject(obj).as[RangeV0](numericRangeV0Codec)
    )
  )

  implicit lazy val rangeV0Encode: EncodeJson[RangeV0] = EncodeJson {
    case NumericRangeV0(min, max) => Json.obj(
      "min" -> min.fold(jNull)(jString),
      "max" -> max.fold(jNull)(jString)
    )
    case SetRangeV0(els) => Json.array(els.map (el => el.fold(jNull)(jString)): _*)
  }
}

object MetadataJsonV0 {
  import CodecsV0._

  def read(json: Json): Option[MetadataJsonV0] = json.array map { jsArray =>
    val featureList = jsArray.flatMap(readFeature)
    MetadataJsonV0(featureList)
  }

  def write(md: MetadataJsonV0): Json = md.features.asJson

  def readFeature(json: Json): Option[FeatureMetadataV0] = json.as[FeatureMetadataV0].toOption


}
