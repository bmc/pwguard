package util

import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Result

/** JSON helpers.
  */
object JsonHelpers {

  import play.api.mvc.Results.Status

  /** Given a status value, such as `Ok`, `BadRequest`, etc., and a
    * JSON value, produce a `Result` object that encodes the JSON for
    * AngularJS, which includes protecting against the JSON with Padding
    * vulnerability.
    *
    * @param status  the status value
    * @param js      the JSON
    *
    * @return the encoded result
    */
  def angularJson(status: Status, js: JsValue): Result = {
    status(s")]}',\n${js.toString}").as("application/json")
  }

  /** Add fields to a JSON object.
    *
    * @param json    the JSON object
    * @param fields  the fields to add
    *
    * @return the new JSON
    */
  def addFields(json: JsValue, fields: (String, JsValue)*): JsValue = {
    // JsValue doesn't have a "+", but JsObject does. This downcast,
    // while regrettable, is pretty much the only option.
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

  /** Convert a `JsError` into an error messages.
    *
    * @param jsError  the error object
    *
    * @return a string
    */
  def jsErrorToString(jsError: JsError): String = {
    jsError.errors.map {
      case (jsPath, validationErrors) => {
        s"$jsPath: " + validationErrors.map { _.message }.mkString(", ")
      }
    }.
    mkString("; ")
  }
}
