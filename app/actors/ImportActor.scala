package actors

import java.io.File

import akka.actor.Actor
import com.github.tototoshi.csv.CSVReader
import dbservice.DAO._
import exceptions._
import models.{User, UserHelpers, PasswordEntry}
import pwguard.global.Globals.ExecutionContexts.Default._
import util.EitherOptionHelpers._

import play.api.Logger

import scala.collection.mutable.{Queue => MutableQueue}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object ImportFieldMapping extends Enumeration {
  type ImportFieldMapping = Value

  val Name        = Value
  val Description = Value
  val Login       = Value
  val Password    = Value
  val URL         = Value
  val Notes       = Value

  val Required = Set(Name, Description)

  val valuesInOrder = values.toList.sorted

  def isRequired(f: ImportFieldMapping): Boolean = Required contains f
}

case class ImportData(csv:      File,
                      mappings: Map[String, String],
                      user:     User,
                      promise:  Promise[Int])

private case class MappedHeaders(nameHeader:  String,
                                 descHeader:  Option[String],
                                 loginHeader: Option[String],
                                 notesHeader: Option[String],
                                 pwHeader:    Option[String],
                                 urlHeader:   Option[String])

/** This actor handles imports, single threading them. Too many concurrent
  * imports cause failure in SQLite, with threads aborting due to SQLite
  * lock errors. This actor controls the flow by applying the imports one
  * at a time.
  */
class ImportActor extends Actor {
  private val logger = Logger("pwguard.actors.ImportActor")
  import ImportFieldMapping._

  def receive = {
    case n: ImportData => {
      processNewImport(n)
    }
  }

  private def processNewImport(data: ImportData): Unit = {

    Try {
      logger.debug(s"Handling new import: $data")

      val reader      = CSVReader.open(data.csv)
      val mappings    = data.mappings

      val headers = MappedHeaders(
        nameHeader  = mappingFor(ImportFieldMapping.Name, mappings).get,
        descHeader  = mappingFor(ImportFieldMapping.Description, mappings),
        loginHeader = mappingFor(ImportFieldMapping.Login, mappings),
        notesHeader = mappingFor(ImportFieldMapping.Notes, mappings),
        pwHeader    = mappingFor(ImportFieldMapping.Password, mappings),
        urlHeader   = mappingFor(ImportFieldMapping.URL, mappings)
      )

      // processNext() is recursive, allowing us to chain an arbitrary
      // number of futures, one after another, each one processing a single
      // row from the spreadsheet. However, a large enough spreadsheet will
      // blow the stack. The number is in the thousands, typically, but it's
      // still not impossible to hit it.

      processNext(0, reader.allWithHeaders(), headers, data.user) map { count =>
        // All done. Complete the promise.
        data.promise.complete(Success(count))
      }
    } recover {
      case NonFatal(e) => {
        logger.error("Error processing import", e)
        data.promise.failure(e)
      }
    }

  }

  def processNext(count:   Int,
                  rows:    List[Map[String, String]],
                  headers: MappedHeaders,
                  user:    User): Future[Int] = {
    rows match {
      case Nil => Future.successful(count)

      case row :: rest => loadOne(row, headers, user) flatMap { loaded =>
        processNext(count + loaded, rest, headers, user)
      }
    }
  }

  private def loadOne(row:     Map[String, String],
                      headers: MappedHeaders,
                      user:    User): Future[Int] = {
    val name = row.get(headers.nameHeader).getOrElse {
      throw new ImportFailed("Missing required name field.")
    }

    val entry = PasswordEntry(
      id                = None,
      userID            = user.id.get,
      name              = name,
      description       = headers.descHeader.flatMap(row.get(_)),
      loginID           = headers.loginHeader.flatMap(row.get(_)),
      encryptedPassword = None,
      url               = headers.urlHeader.flatMap(row.get(_)),
      notes             = headers.notesHeader.flatMap(row.get(_))
    )

    saveIfNew(entry, headers.pwHeader.flatMap(row.get(_)), user) map { opt =>
      // Return a count of 1 if saved, 0 if not.
      opt.map(_ => 1).getOrElse(0)
    }
  }

  private def mappingFor(key: ImportFieldMapping, mappings: Map[String, String]):
    Option[String] = {

    val opt = mappings.get(key.toString)
    if ((ImportFieldMapping.isRequired(key)) && opt.isEmpty) {
      throw new ImportFailed(s"Missing required mapping key $key")
    }
    opt
  }

  private def saveIfNew(entry:                PasswordEntry,
                        plaintextPasswordOpt: Option[String],
                        user:                 User):
    Future[Option[PasswordEntry]] = {

    val futureFuture: Future[Future[Option[PasswordEntry]]] =
      for { epwOpt   <- maybeEncryptPW(user, plaintextPasswordOpt)
            entryOpt <- passwordEntryDAO.findByName(user, entry.name) }
      yield {
        if (entryOpt.isDefined) {
          logger.debug {
            s"Won't update existing ${entry.name} entry for ${user.email}"
          }
          Future.successful(None)
        }
        else {
          val toSave = entry.copy(encryptedPassword = epwOpt)
          passwordEntryDAO.save(toSave) map { Some(_) }
        }
      }

    futureFuture.flatMap {f => f}
  }

  def maybeEncryptPW(user: User, password: Option[String]):
    Future[Option[String]] = {

    password map { pw =>
      UserHelpers.encryptStoredPassword(user, pw) map { Some(_) }
    } getOrElse {
      Future.successful(noneT[String])
    }
  }
}
