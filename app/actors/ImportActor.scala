package actors

import java.io.File

import akka.actor.Actor
import models.{User, UserHelpers, PasswordEntry}

import play.api.Logger
import services.{ImportData, ImportExportService}

import scala.concurrent.Promise



/** This actor handles imports, single threading them. Too many concurrent
  * imports cause failure in SQLite, with threads aborting due to SQLite
  * lock errors. This actor controls the flow by applying the imports one
  * at a time.
  */
class ImportActor extends Actor {
  private val logger = Logger("pwguard.actors.ImportActor")

  def receive = {
    case n: ImportData => {
      ImportExportService.processNewImport(n)
    }
  }

}
