package util

import java.io.File

import pwguard.global.Globals.ExecutionContexts.Default._

import scala.concurrent.Future

object FileHelpers {

  private val rng = new java.security.SecureRandom()

  /** Create a randomly generated file name that is *not* a temporary file,
    * so that the file will *not* be deleted by the destructor. This strategy
    * is necessary because we might be storing the filename in the cache
    * between requests. If the cache is not in memory (e.g., we're using
    * Memcached), the File object may be cleaned up. If it's a temporary file
    * the File destructor will delete the file.
    *
    * The resulting file will behave like a temporary file, in that it will
    * be deleted upon JVM exit. However, it will *not* be deleted by the
    * File class's destructor.
    *
    * @param prefix  the file name prefix
    * @param suffix  the file name suffix
    *
    * @return a `Future[File]`
    */
  def createPseudoTempFile(prefix: String, suffix: String): Future[File] = {

    import scala.collection.JavaConversions.propertiesAsScalaMap
    import scala.collection.mutable.{Map => MutableMap}

    Future {
      val random = rng.nextLong
      val mutableProps: MutableMap[String, String] = System.getProperties
      val props = mutableProps.toMap
      val tmp = props.getOrElse("java.io.tmpdir", "/tmp")
      val file = new File(s"${tmp}/${prefix}${random}${suffix}")
      file.createNewFile()
      file.deleteOnExit()
      file
    }
  }
}
