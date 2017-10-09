package com.ardentex.pwguard

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/** Consolidates Json support in one place.
  */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import browscap._

  implicit object CapFormat extends RootJsonFormat[BrowserCapabilities] {
    def read(value: JsValue) = ???

    def write(c: BrowserCapabilities) = {
      JsObject(
        "browser"             -> JsString(c.browser),
        "browserType"         -> JsString(c.browserType.toString),
        "browserMajorVersion" -> JsString(c.browserMajorVersion),
        "deviceType"          -> JsString(c.deviceType.toString),
        "platform"            -> JsString(c.platform),
        "platformVersion"     -> JsString(c.platformVersion),
        "isMobile"            -> JsBoolean(c.isMobile)
      )
    }
  }
}
