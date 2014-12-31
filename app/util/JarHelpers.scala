package util

import scala.concurrent.Future
import pwguard.global.Globals.ExecutionContexts.Default._
import scala.collection.JavaConversions.enumerationAsScalaIterator
import scala.collection.JavaConversions.asScalaSet
import scala.collection.mutable.{Set => MutableSet}
import java.net.URL
import java.util.jar.{Manifest => JarManifest}

object JarHelpers {

  /** Find the Jar Manifest that contains a specific key/value.
    *
    * @param key   the key
    * @param value the value
    *
    * @return a Future containing an Option of a Map, the map representing
    *         the keys and values of the located manifest
    */
  def matchingManifest(key: String, value: String):
    Future[Option[Map[String, String]]] = {

    def isCorrectManifest(m: Map[String, String]): Boolean = {
      m.get(key).map { _ == value }.getOrElse(false)
    }

    Future {
           // Returns the manifest if it is the correct one.

      val classLoader = getClass.getClassLoader
      classLoader.getResources("META-INF/MANIFEST.MF")

    } flatMap { resources =>

      Future.sequence {
        resources.map { loadManifest(_) }
      } map { seq =>
        seq.collect { case m if isCorrectManifest(m) => m }.toSeq.headOption
      }
    }
  }

  /** Load a manifest from the specified URL.
    *
    * @param url  The URL, which must point to a `java.util.jar.Manifest`
    *             resource
    *
    * @return `Future[Map[String, String]]` representing the manifest
    */
  def loadManifest(url: URL): Future[Map[String, String]] = {
    Future {
      val manifest = new JarManifest(url.openStream())
      val attrs    = manifest.getMainAttributes

      val keyObjects: MutableSet[AnyRef] = attrs.keySet
      keyObjects.map { keyObj: AnyRef =>
        val key = keyObj.toString
        key -> Option(attrs.getValue(key))
      }.
      collect {
        case (k: String, Some(v: String)) => k -> v
      }.
      toMap
    }
  }
}
