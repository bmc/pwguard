package services

import actors.UserAgentDecoderActor
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps;
import util.UserAgent.UserAgent

import scala.concurrent.Future

/** Service to decode user agent strings. Hides the underlying implementation
  * (which is, currently, actor-based).
  */
class UserAgentDecoder(akkaSystem: ActorSystem) {

  private val decoderActor = akkaSystem.actorOf(Props[UserAgentDecoderActor])

  def decodeUserAgent(ua: String): Future[UserAgent] = {
    ask(decoderActor, ua)(Timeout(5 seconds)).mapTo[UserAgent]
  }
}
