import org.scalatestplus.play._

import play.api.test._
import play.api.test.Helpers._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class IntegrationSpec
  extends PlaySpec
  with OneServerPerSuite
  with OneBrowserPerSuite
  with HtmlUnitFactory {
/*
  "MainController" must {

    "work from within a browser" in {

      go to (s"http://localhost:9000")

      println(s"*** $pageTitle")
      pageTitle must include ("PWGuard")
    }
  }
  */
}
