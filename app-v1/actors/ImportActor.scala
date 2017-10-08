package actors

import akka.actor.Actor

import play.api.Logger
import services.{ImportCSVData, ImportXMLData, ImportExportService}


/** This actor handles imports, single threading them. Too many concurrent
  * imports cause failure in SQLite, with threads aborting due to SQLite
  * lock errors. This actor controls the flow by applying the imports one
  * at a time.
  */
class ImportActor extends Actor {
  private val logger = Logger("pwguard.actors.ImportActor")

  def receive = {
    case d: ImportCSVData => ImportExportService.processNewCSVImport(d)
    case d: ImportXMLData => ImportExportService.processNewXMLImport(d)
  }

}
