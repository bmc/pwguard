package models

import play.api.libs.json.JsValue

/** Base trait for all models.
  */
trait BaseModel {
  val id: Option[Int]

  /** Default implementation of `equals()`, based on the ID.
    *
    * @param other the other object
    *
    * @return `true` if equal (based on ID), `false` otherwise.
    */
  override def equals(other: Any): Boolean = {
    other match {
      case that: BaseModel => {
        val eqOpt = for { thisID <- this.id
                          thatID <- that.id }
                    yield thisID == thatID
        eqOpt.getOrElse(false)
      }

      case _ => false
    }
  }

  /** Default implementation of `hashCode`, based on the ID. An empty ID is
    * treated as a 0, for the purposes of calculating a hash code.
    *
    * @return the hash code
    */
  override def hashCode = id.getOrElse(0).hashCode
}
