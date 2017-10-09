package com.ardentex.pwguard.browscap

import com.blueconic.browscap.{UserAgentParser, UserAgentService}

import scala.util.Try

object BrowserType extends Enumeration {
  type BrowserType = Value

  val Browser        = Value
  val Application    = Value
  val BotCrawler     = Value
  val OfflineBrowser = Value
  val Other          = Value
}

object DeviceType extends Enumeration {
  type DeviceType = Value

  val MobilePhone = Value
  val Desktop     = Value
  val Tablet      = Value
  val Console     = Value
  val TVDevice    = Value
  val Other       = Value
}

import BrowserType.BrowserType, DeviceType.DeviceType

final case class BrowserCapabilities(browser:             String,
                                     browserType:         BrowserType,
                                     browserMajorVersion: String,
                                     deviceType:          DeviceType,
                                     platform:            String,
                                     platformVersion:     String) {
  val isMobile = {
    (deviceType == DeviceType.MobilePhone) ||
    (deviceType == DeviceType.Tablet)
  }
}

/** Front-end to Java Browscap library, providing a more Scala-idiomatic
  * interface.
  */
final class Browscap(parser: UserAgentParser) {

  /** Parse a user-agent string into its browser capabilities.
    *
    * @param ua  the user-agent string
    *
    * @return A `BrowserCapabilities` object
    */
  def parseUserAgent(ua: String): BrowserCapabilities = {
    val cap = parser.parse(ua)
    val browserType = cap.getBrowserType match {
      case "Browser"         => BrowserType.Browser
      case "Application"     => BrowserType.Application
      case "Offline Browser" => BrowserType.OfflineBrowser
      case "Bot/Crawler"     => BrowserType.BotCrawler
      case _                 => BrowserType.Other
    }

    val deviceType = cap.getDeviceType match {
      case "Mobile Phone" => DeviceType.MobilePhone
      case "Desktop"      => DeviceType.Desktop
      case "Tablet"       => DeviceType.Tablet
      case "Console"      => DeviceType.Console
      case "TV Device"    => DeviceType.TVDevice
      case _              => DeviceType.Other
    }

    BrowserCapabilities(
      browser             = cap.getBrowser,
      browserType         = browserType,
      deviceType          = deviceType,
      browserMajorVersion = cap.getBrowserMajorVersion,
      platform            = cap.getPlatform,
      platformVersion     = cap.getPlatformVersion
    )
  }
}

object Browscap {

  def apply(): Try[Browscap] = {
    Try {
      val parser = (new UserAgentService).loadParser
      new Browscap(parser)
    }
  }
}
