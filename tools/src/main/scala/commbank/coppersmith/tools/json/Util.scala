package commbank.coppersmith.tools.json

import argonaut.Argonaut._
import argonaut.{JsonObject, Json, EncodeJson}
import scalaz._, Scalaz._

object Util {
  def stripNullValuesFromObjects[A](enc: EncodeJson[A]): EncodeJson[A] = new EncodeJson[A] {
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
}
