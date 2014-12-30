package controllers

import dbservice.DAO
import models.{UserHelpers, User, PasswordEntry}
import util.EitherOptionHelpers._

import com.github.tototoshi.csv._

import grizzled.io.util._

import java.io._

import play.api._
import play.api.libs.json.{JsString, Json, JsValue}
import play.api.mvc.Results._
import play.api.mvc.BodyParsers._
import play.api.cache.Cache
import play.api.Play.current

import pwguard.global.Globals.ExecutionContexts.Default._

import scala.concurrent.Future
import scala.util.control.NonFatal

class UploadFailed(msg: String) extends Exception(msg)
class ImportFailed(msg: String) extends Exception(msg)
class ExportFailed(msg: String) extends Exception(msg)

object ImportExportController extends BaseController {

  override val logger = Logger("pwguard.controllers.ImportExportController")

  import DAO.passwordEntryDAO

  object Field extends Enumeration {
    type Field = Value

    val Name        = Value
    val Description = Value
    val Login       = Value
    val Password    = Value
    val URL         = Value
    val Notes       = Value

    val Required = Set(Name, Description)

    val valuesInOrder = values.toList.sorted
  }

  val XSLXContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  val CSVContentType  = "text/csv"

  private val FileCacheKey = "uploaded-file"

  private val ExcelContentTypes = Set(
    "application/vnd.ms-excel",
    XSLXContentType
  )

  private val CSVContentTypes = Set(
    CSVContentType,
    "application/csv",
    "text/comma-separated-values"
  )

  private val BaseExportFilename = "passwords"

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def exportData(format: String) = SecuredAction { authReq =>

    def unpackEntry(user: User, e: PasswordEntry):
      Future[Map[Field.Value, String]] = {

      val PasswordEntry(_, _, name, descriptionOpt, loginIDOpt,
                        encryptedPasswordOpt, urlOpt, notesOpt) = e
      val description = descriptionOpt.getOrElse("")
      val loginID     = loginIDOpt.getOrElse("")
      val notes       = notesOpt.getOrElse("")
      val url         = urlOpt.getOrElse("")

      encryptedPasswordOpt map { epw =>
        UserHelpers.decryptStoredPassword(user, epw)

      } getOrElse {
        Future.successful("")

      } map { pwString =>
        Map(Field.Name        -> name,
            Field.Password    -> pwString,
            Field.Description -> description,
            Field.Login       -> loginID,
            Field.URL         -> url,
            Field.Notes       -> notes)
      }
    }

    def createDownload(entries: Set[PasswordEntry], user: User):
      Future[(File, String)] = {

      Future {
        val out = File.createTempFile("pwguard", format)
        out.deleteOnExit()
        out

      } flatMap { out: File =>

        val entryFutures = entries.map { unpackEntry(user, _) }.toSeq
        for { seqOfFutures <- Future.sequence(entryFutures) }
        yield (out, seqOfFutures)

      } flatMap { case (out, entries) =>

        format match {
          case "xlsx" => writeExcel(out, entries).map { (_, XSLXContentType) }
          case "csv"  => writeCSV(out, entries).map { (_, CSVContentType) }
          case _      => throw new ExportFailed("Unknown format")
        }
      }
    }

    // Main logic

    val r = for { seq          <- passwordEntryDAO.allForUser(authReq.user)
                 (file, mime) <- createDownload(seq, authReq.user) }
            yield {
              val filename = s"${BaseExportFilename}.${format}"
              Ok.sendFile(file)
                .as(mime)
                .withHeaders("Content-disposition" ->
                             s"attachment; filename=$filename")
            }

    r recover {
      case NonFatal(e) =>
        BadRequest(jsonError(e))
    }
  }

  def importDataUpload = SecuredAction(parse.multipartFormData) { authReq =>
    implicit val request = authReq.request

    request.body.file("file") map { uploaded =>
      // uploaded.ref is a play.api.libs.Files.TemporaryFile,
      // with a file member

      def header(r: CSVReader): Option[List[String]] = {
        r.readNext().flatMap { list =>
          list.filter(_.trim.length > 0) match {
            case Nil => None
            case l   => Some(l)
          }
        }
      }

      getCSVReader(uploaded.ref.file, uploaded.contentType) map {
        case (f: File, reader: CSVReader) => {

          val jsonOpt = for { h  <- header(reader) }
          yield {
            Cache.set(FileCacheKey, f)
            Ok(
              Json.obj(
                "headers" -> h,
                "fields"  -> Field.values.map { field =>
                  Json.obj("name" -> field.toString,
                           "required" -> Field.Required.contains(field))
                }
              )
            )
          }

          jsonOpt.getOrElse {
            throw new UploadFailed("Empty file.")
          }
        }
      }
    } getOrElse {
      Future.failed(new UploadFailed("No uploaded file."))

    } recover {
      case NonFatal(e) => BadRequest(jsonError(e))
    }
  }

  def completeImport = SecuredJSONAction { authReq =>
    def getFile(): Future[File] = {
      Future {
        Cache.getAs[File](FileCacheKey).getOrElse {
          throw new ImportFailed("No previously uploaded file.")
        }
      }
    }

    def getMappings(): Future[Map[String, String]] = {
      Future {
        val json = authReq.request.body
        (json \ "mappings").asOpt[Map[String, String]].getOrElse {
          throw new ImportFailed("No mappings.")
        }
      }
    }

    def getReader(file: File): Future[CSVReader] = {
      Future {
        CSVReader.open(file)
      }
    }

    def mappingFor(key: Field.Value, mappings: Map[String, String]):
      Option[String] = {

      val sKey = key.toString
      val opt = mappings.get(sKey)
      if ((Field.Required contains key) && opt.isEmpty)
        throw new ImportFailed(s"Required mapping $sKey not found")
      opt
    }

    def maybeEncryptPW(password: Option[String]): Future[Option[String]] = {
      password map { pw =>
        UserHelpers.encryptStoredPassword(authReq.user, pw) map { Some(_) }
      } getOrElse {
        Future.successful(noneT[String])
      }
    }

    def saveIfNew(name:     String,
                  desc:     Option[String],
                  login:    Option[String],
                  password: Option[String],
                  urlOpt:   Option[String],
                  notes:    Option[String]): Future[Option[PasswordEntry]] = {
      val user = authReq.user

      val futureFuture: Future[Future[Option[PasswordEntry]]] =
        for { epwOpt   <- maybeEncryptPW(password)
              entryOpt <- passwordEntryDAO.findByName(user, name) }
        yield {
          if (entryOpt.isDefined) {
            logger.debug(s"Won't update existing $name entry for ${user.email}")
            Future.successful(None)
          }
          else {
            val entry = PasswordEntry(id                = None,
                                      userID            = user.id.get,
                                      name              = name,
                                      description       = desc,
                                      loginID           = login,
                                      encryptedPassword = epwOpt,
                                      url               = urlOpt,
                                      notes             = notes)
            passwordEntryDAO.save(entry) map { Some(_) }
          }
        }

      futureFuture.flatMap {f => f}
    }


    // Get the mappings, find the uploaded file, and open a reader.
    val f =
      for { file     <- getFile()
            mappings <- getMappings()
            reader   <- getReader(file) }
      yield (mappings, reader)

    // If none of those failed, save the mappings in the CSV file, but only
    // if they're new.
    val result = f flatMap {
      case (mappings, reader) => {
        // Use the mappings to find the appropriate headings.
        val nameHeader  = mappingFor(Field.Name, mappings)
        val descHeader  = mappingFor(Field.Description, mappings)
        val loginHeader = mappingFor(Field.Login, mappings)
        val notesHeader = mappingFor(Field.Notes, mappings)
        val pwHeader    = mappingFor(Field.Password, mappings)
        val urlHeader   = mappingFor(Field.URL, mappings)

        Future.sequence {
          // Load each row and map it to an Option[PasswordEntry]. The
          // None entries will correspond to existing DB entries whose names
          // match names in the uploaded file. The Some entries will be the
          // new entries.
          for { map <- reader.allWithHeaders() }
          yield {
            val name = map.get(nameHeader.get).getOrElse {
              throw new ImportFailed("Missing required name field.")
            }
            saveIfNew(name     = name,
                      password = pwHeader.flatMap(map.get(_)),
                      desc     = descHeader.flatMap(map.get(_)),
                      login    = loginHeader.flatMap(map.get(_)),
                      urlOpt   = urlHeader.flatMap(map.get(_)),
                      notes    = notesHeader.flatMap(map.get(_)))

          }
        }
      }

    } map { seq: Seq[Option[PasswordEntry]] =>
      // Get rid of the entries that weren't saved because they weren't
      // new.
      Ok(Json.obj("total" -> seq.flatten.length))

    } recover {
      case NonFatal(e) => BadRequest(jsonError(e))
    }

    getFile() map { f =>
      f.delete()
      Cache.remove(FileCacheKey)
    }

    result
  }

  // -------------------------------------------------------------------------
  // Private Methods
  // -------------------------------------------------------------------------

  private def writeExcel(out: File, entries: Seq[Map[Field.Value, String]]):
    Future[File] = {

    import java.io.FileOutputStream
    import org.apache.poi.xssf.usermodel.XSSFWorkbook

    Future {
      withCloseable(new FileOutputStream(out)) { fOut =>
        val wb = new XSSFWorkbook
        val sheet = wb.createSheet("Passwords")

        // Write the header.
        val font = wb.createFont()
        font.setBold(true)
        val style = wb.createCellStyle()
        style.setFont(font)

        val row = sheet.createRow(0.asInstanceOf[Short])
        for ((cellValue, i) <- Field.valuesInOrder.zipWithIndex) {
          val cell = row.createCell(i)
          cell.setCellValue(cellValue.toString)
          cell.setCellStyle(style)
        }

        // Now the data.
        for { (rowMap, rI)  <- entries.zipWithIndex
              rowNum         = rI + 1
              row            = sheet.createRow(rowNum.asInstanceOf[Short])
              (name, cI)    <- Field.valuesInOrder.zipWithIndex } {
          val cellNum = cI + 1
          val cell    = row.createCell(cI.asInstanceOf[Short])
          cell.setCellValue(rowMap.getOrElse(name, ""))
        }

        wb.write(fOut)
        out
      }
    }
  }

  private def writeCSV(out: File, entries: Seq[Map[Field.Value, String]]):
    Future[File] = {

    Future {
      withCloseable(new BufferedWriter(
        new OutputStreamWriter(
          new FileOutputStream(out), "UTF-8"))) { fOut =>
        withCloseable(CSVWriter.open(fOut)) { csv =>

          // Write the header.
          csv.writeRow(Field.valuesInOrder.map(_.toString).toList)

          // Now the data.
          for ( rowMap <- entries ) {
            val row = Field.valuesInOrder.map { rowMap.getOrElse(_, "") }.toList
            csv.writeRow(row)
          }

          out
        }
      }
    }
  }

  private def getCSVReader(f: File, contentTypeOpt: Option[String]):
    Future[(File, CSVReader)] = {

    contentTypeOpt map { contentType =>
      if (ExcelContentTypes contains contentType)
        convertExcelToCSV(f).map { csv => (csv, CSVReader.open(csv)) }
      else if (CSVContentTypes contains contentType)
        Future { (f, CSVReader.open(f)) }
      else
        Future.failed(new ImportFailed(s"Unknown import file type: $contentType"))
    } getOrElse {
      logger.error(s"No content type posted. Assuming CSV.")
      Future { (f, CSVReader.open(f)) }
    }
  }

  private def convertExcelToCSV(f: File): Future[File] = {
    import org.apache.poi.ss.usermodel.WorkbookFactory
    import scala.collection.JavaConversions.asScalaIterator

    Future {
      logger.debug(s"Converting ${f.getName} to CSV.")
      val wb = WorkbookFactory.create(f)
      val sheet = wb.getNumberOfSheets match {
        case 0 => throw new UploadFailed(s"No worksheets in ${f.getName}")
        case 1 => wb.getSheetAt(0)
        case n => {
          logger.warn(s"$n worksheets in ${f.getName}. Ignoring extras.")
          wb.getSheetAt(0)
        }
      }

      val csvFile = File.createTempFile("pwg", ".csv")

      val writer = CSVWriter.open(csvFile)

      for (row <- sheet.iterator) {
        val cells = row.cellIterator.map { _.toString }.toList
        writer.writeRow(cells)
      }

      writer.close()
      f.delete()
      csvFile.deleteOnExit()
      csvFile
    }
  }
}
