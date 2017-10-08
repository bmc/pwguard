package com.ardentex.pwguard

import akka.actor.ActorSystem
import akka.event.LogSource

/**
  */
trait LoggingSupport {
  implicit val system: ActorSystem

  private implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  def log = akka.event.Logging(system, this)
}
