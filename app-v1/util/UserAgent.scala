package util.UserAgent

import net.sf.uadetector.service.UADetectorServiceFactory
import net.sf.uadetector.{UserAgentType => UADType, ReadableDeviceCategory, VersionNumber, ReadableUserAgent}
import util.UserAgent.DeviceCategory.DeviceCategory
import scala.collection.JavaConverters._
import util.UserAgent.UserAgentType.UserAgentType

/** Decoded user agent string.
  */
case class UserAgent(name:            String,
                     operatingSystem: OperatingSystem,
                     family:          String,
                     agentType:       UserAgentType,
                     version:         VersionIdentifier,
                     deviceCategory:  DeviceCategory) {

  lazy val isMobile = {
    (deviceCategory == DeviceCategory.PDA) ||
    (deviceCategory == DeviceCategory.SmartPhone) ||
    (deviceCategory == DeviceCategory.Tablet) ||
    (deviceCategory == DeviceCategory.WearableComputer)
  }
}

object UserAgentType extends Enumeration {
  type UserAgentType = Value

  val Browser        = Value
  val EmailClient    = Value
  val FeedReader     = Value
  val Library        = Value
  val MediaPlayer    = Value
  val MobileBrowser  = Value
  val OfflineBrowser = Value
  val Robot          = Value
  val Anonymizer     = Value
  val Validator      = Value
  val WAPBrowser     = Value
  val Other          = Value
  val Unknown        = Value
}

object DeviceCategory extends Enumeration {
  type DeviceCategory = Value

  val GameConsole = Value
  val PersonalComputer = Value
  val SmartTV          = Value
  val SmartPhone       = Value
  val Tablet           = Value
  val WearableComputer = Value
  val PDA              = Value
  val Other            = Value
  val Unknown          = Value
}

case class VersionIdentifier(major:     String,
                             minor:     String,
                             extension: String,
                             groups:    Seq[String],
                             bugFix:    String)

case class OperatingSystem(name:          String,
                           family:        String,
                           producer:      String,
                           producerURL:   String,
                           url:           String,
                           version:       VersionIdentifier)


object UserAgent {
  private val UATypeMap = Map[UADType, UserAgentType](
    UADType.BROWSER              -> UserAgentType.Browser,
    UADType.EMAIL_CLIENT         -> UserAgentType.EmailClient,
    UADType.FEED_READER          -> UserAgentType.FeedReader,
    UADType.LIBRARY              -> UserAgentType.Library,
    UADType.MEDIAPLAYER          -> UserAgentType.MediaPlayer,
    UADType.MOBILE_BROWSER       -> UserAgentType.MobileBrowser,
    UADType.OFFLINE_BROWSER      -> UserAgentType.OfflineBrowser,
    UADType.ROBOT                -> UserAgentType.Robot,
    UADType.USERAGENT_ANONYMIZER -> UserAgentType.Anonymizer,
    UADType.VALIDATOR            -> UserAgentType.Validator,
    UADType.WAP_BROWSER          -> UserAgentType.WAPBrowser,
    UADType.OTHER                -> UserAgentType.Other,
    UADType.UNKNOWN              -> UserAgentType.Unknown
  )

  import ReadableDeviceCategory.{Category => UADevCategory}

  private val DeviceTypeMap = Map[UADevCategory, DeviceCategory](
    UADevCategory.GAME_CONSOLE      -> DeviceCategory.GameConsole,
    UADevCategory.PERSONAL_COMPUTER -> DeviceCategory.PersonalComputer,
    UADevCategory.SMART_TV          -> DeviceCategory.SmartTV,
    UADevCategory.SMARTPHONE        -> DeviceCategory.SmartPhone,
    UADevCategory.TABLET            -> DeviceCategory.Tablet,
    UADevCategory.WEARABLE_COMPUTER -> DeviceCategory.WearableComputer,
    UADevCategory.PDA               -> DeviceCategory.PDA,
    UADevCategory.OTHER             -> DeviceCategory.Other,
    UADevCategory.UNKNOWN           -> DeviceCategory.Unknown

  )

  private val uaParser = UADetectorServiceFactory.getResourceModuleParser

  /** Create a `UserAgent` from a `ReadableUserAgent`.
    *
    * @param rua The `ReadableUserAgent` object
    *
    * @return Our `UserAgent` object
    */
  def apply(rua: ReadableUserAgent): UserAgent = convert(rua)

  /** Create a `UserAgent` from a String. Note that there's no way this thing
    * can fail, unless passed a null pointer.
    *
    * @param string The user agent string
    *
    * @return The `UserAgent` object, which may or may not contain useful data
    */
  def apply(string: String): UserAgent = {
    convert(uaParser.parse(string))
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def convert(rua: ReadableUserAgent): UserAgent = {

    val ruOS = rua.getOperatingSystem

    val os = OperatingSystem(
      name        = ruOS.getName,
      family      = ruOS.getFamilyName,
      producer    = ruOS.getProducer,
      producerURL = ruOS.getProducerUrl,
      url         = ruOS.getUrl,
      version     = convertVersion(ruOS.getVersionNumber)
    )

    val uaType = UATypeMap.get(rua.getType).getOrElse(UserAgentType.Unknown)
    val dev    = DeviceTypeMap.get(rua.getDeviceCategory.getCategory).
                               getOrElse(DeviceCategory.Unknown)
    UserAgent(
      name            = rua.getName,
      operatingSystem = os,
      family          = rua.getFamily.getName,
      agentType       = uaType,
      version         = convertVersion(rua.getVersionNumber),
      deviceCategory  = dev
    )
  }

  private def convertVersion(ruv: VersionNumber): VersionIdentifier = {
    VersionIdentifier(
      major     = ruv.getMajor,
      minor     = ruv.getMinor,
      extension = ruv.getExtension,
      groups    = ruv.getGroups.asScala,
      bugFix    = ruv.getBugfix
      )
  }
}
