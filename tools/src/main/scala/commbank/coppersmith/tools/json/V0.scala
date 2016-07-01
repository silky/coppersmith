package commbank.coppersmith.tools.json

import argonaut._, Argonaut._


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
  featureType: String)

object MetadataJsonV0 {
  def read(json: Json): Option[MetadataJsonV0] = json.array map { jsArray =>
    val featureList = jsArray.flatMap(readFeature)
    MetadataJsonV0(featureList)
  }

  def readFeature(json: Json): Option[FeatureMetadataV0] =
    json.as[FeatureMetadataV0].toOption

  implicit def featureMetadataV0Decode: DecodeJson[FeatureMetadataV0] =
    jdecode7L(FeatureMetadataV0.apply)(
      "namespace",
      "name",
      "description",
      "source",
      "typesConform",
      "valueType",
      "featureType")
}
