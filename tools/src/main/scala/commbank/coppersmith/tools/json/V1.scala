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
                               sourceType: String,
                               sources: List[String],
                               typesConform: Boolean,
                               valueType: String,
                               featureType: String)

object MetadataJsonV1 {
  implicit lazy val decodeMetadataJsonV1: CodecJson[MetadataJsonV1] =
    casecodec2(MetadataJsonV1.apply, MetadataJsonV1.unapply)("version", "featureSets")

  implicit lazy val decodeFeatureSetMetadataV1: CodecJson[FeatureSetMetadataV1] =
    casecodec2(FeatureSetMetadataV1.apply, FeatureSetMetadataV1.unapply)("featureSet", "features")

  implicit lazy val decodeFeatureMetadataV1: CodecJson[FeatureMetadataV1] =
    casecodec8(FeatureMetadataV1.apply, FeatureMetadataV1.unapply)(
      "namespace",
      "name",
      "description",
      "sourceType",
      "sources",
      "typesConform",
      "valueType",
      "featureType")

  def read(json: Json): Option[MetadataJsonV1] = json.as[MetadataJsonV1].toOption
  def write(md: MetadataJsonV1): Json = md.asJson
}
