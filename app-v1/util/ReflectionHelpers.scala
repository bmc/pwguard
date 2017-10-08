package util

/** Helper functions to make certain aspects of the Scala reflection API
  * easier to use.
  */
object ReflectionHelpers {
  import scala.reflect.runtime.{universe ⇒ ru}

  /** Get the short name of a reflected type tag.
    */
  def typeTagShortName[M](tag: ru.TypeTag[M]): String = {
    tag.tpe.toString.split("""\.""").last
  }

  /** Get the short name of a class.
    *
    * @param cls  the class
    *
    * @return the short name
    */
  def classShortName[T](cls: Class[T]): String = {
    cls.getName.split("""\.""").last
  }
}
