package commbank.coppersmith.tools.json

import argonaut._

trait MetadataJson {
  def version: Int
}

object MetadataJson {
  def read(json: Json): Option[MetadataJson] = {
    val result:Option[MetadataJson] = readVersion(json).map {
      case 1 => MetadataJsonV1.read(json)
      case v => sys.error(s"Unsupported version: $v")
    } getOrElse MetadataJsonV0.read(json)
    result
  }

  def readVersion(json: Json) = for {
    field <- json.field("version")
    num <- field.number
    int <- num.toInt
  } yield int
}