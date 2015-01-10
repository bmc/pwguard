import org.scalatestplus.play._

import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends PlaySpec with OneAppPerSuite {

  "MainController" must {

    "render the index page" in {
      val home = route(FakeRequest(GET, "/pwguard/")).get

      status(home) mustBe (OK)
    }
  }
}
