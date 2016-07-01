package commbank.coppersmith.tools.json

import argonaut._, Argonaut._

case class MetadataJsonV1(version: Int, featureSets: List[FeatureSetMetadataV1]) extends MetadataJson {
  require(version == 1)
}

case class FeatureSetMetadataV1(featureSet: String, features: List[FeatureMetadataV1])

case class FeatureMetadataV1 (
                               namespace: String,
                               name:String,
                               description: String,
                               source: String,
                               typesConform: Boolean,
                               valueType: String,
                               featureType: String)

object MetadataJsonV1 {
  implicit lazy val decodeMetadataJsonV1: DecodeJson[MetadataJsonV1] =
    jdecode2L(MetadataJsonV1.apply)("version", "featureSets")

  implicit lazy val decodeFeatureSetMetadataV1: DecodeJson[FeatureSetMetadataV1] =
    jdecode2L(FeatureSetMetadataV1.apply)("featureSet", "features")

  implicit lazy val decodeFeatureMetadataV1: DecodeJson[FeatureMetadataV1] =
    jdecode7L(FeatureMetadataV1.apply)(
      "namespace",
      "name",
      "description",
      "source",
      "typesConform",
      "valueType",
      "featureType")

  def read(json: Json): Option[MetadataJsonV1] = json.as[MetadataJsonV1].toOption
}
