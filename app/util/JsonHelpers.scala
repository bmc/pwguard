package util

import play.api.libs.json.{Writes, Json, JsObject, JsValue}

/** JSON helpers.
  */
object JsonHelpers {

  /** Add fields to a JSON object.
    *
    * @param json    the JSON object
    * @param fields  the fields to add
    *
    * @return the new JSON
    */
  def addFields(json: JsValue, fields: (String, JsValue)*): JsValue = {
    val obj = json.as[JsObject]
    JsObject(obj.fields ++ fields)
  }

  /** Remove fields from a JSON object.
    *
    * @param json  the JSON object
    * @param keys  the keys of the fields to remove
    *
    * @return the new JSON
    */
  def removeFields(json: JsValue, keys: String*): JsValue = {
    val obj    = json.as[JsObject]
    val keySet = keys.toSet
    val fields = obj.fields.filter { case (key, _) =>
      ! (keySet contains key)
    }
    JsObject(fields)
  }
}
