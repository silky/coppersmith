package commbank.coppersmith.tools.json

import argonaut._, Argonaut._
import scalaz._, Scalaz._

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
case class NumericRangeV0(start: String, end: String) extends RangeV0

case class SetRangeV0(elements: List[String]) extends RangeV0

object CodecsV0 {
  // The macro-derived encoder outputs nulls. We don't want to
  private def stripNullValuesFromObjects[A](enc: EncodeJson[A]): EncodeJson[A] = new EncodeJson[A] {
    def encode(a: A): Json = {
      val obj = enc.encode(a)
      obj.withObject { o =>
        val objMap = o.toMap
        val newFields: List[(JsonField, Json)] = o.fields.flatMap { f=>
          val v = objMap(f)
          if (v.isNull) None else Some(f -> v)
        }
        JsonObject.from(newFields)
      }
    }
  }

  implicit lazy val featureMetadataV0Codec: CodecJson[FeatureMetadataV0] =
    CodecJson.derived(stripNullValuesFromObjects(EncodeJson.derive), DecodeJson.derive)

  lazy val numericRangeV0Codec = CodecJson.derive[NumericRangeV0].map(it => it : RangeV0)


  implicitly[Applicative[DecodeResult]]

  implicit lazy val rangeV0Decode: DecodeJson[RangeV0] = DecodeJson(
    c => c.focus.arrayOrObject(
      DecodeResult.fail("Either an array or JSON object expected", c.history),
      arr => arr.map((jsn: Json) => jsn.as[String]).sequenceU.map(SetRangeV0.apply),
      obj =>  jObject(obj).as[RangeV0](numericRangeV0Codec)
    )
  )

  implicit lazy val rangeV0Encode: EncodeJson[RangeV0] = EncodeJson {
    case NumericRangeV0(start, end) => Json.obj(
      "start" -> jString(start),
      "end" -> jString(end)
    )
    case SetRangeV0(els) => Json.array(els.map(jString): _*)
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
