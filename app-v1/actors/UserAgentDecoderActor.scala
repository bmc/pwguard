package actors

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import com.google.common.cache.{Cache, CacheBuilder}
import play.api.Logger
import util.UserAgent._


/** Decodes a user agent string. This is pushed out to an actor because it
  * can take some time.
  */
class UserAgentDecoderActor extends Actor {

  private val logger = Logger("pwguard.actors.UserAgentDecoderActor")

  // Local cache of retrieved results.
  private val cache: Cache[String, UserAgent] =
    CacheBuilder.newBuilder
                .maximumSize(100)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build()

  def receive = {
    case userAgentString: String => {
      val result = Option(cache.getIfPresent(userAgentString))

      sender ! result.getOrElse {
        logger.debug(s"Getting uncached data for User-Agent $userAgentString")
        val ua = UserAgent(userAgentString)
        cache.put(userAgentString, ua)
        ua
      }
    }
  }
}
