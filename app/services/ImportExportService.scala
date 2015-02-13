package services

import java.io.{OutputStreamWriter, FileOutputStream, BufferedWriter, File}

import com.github.tototoshi.csv.{CSVReader, CSVWriter}
import org.apache.poi.ss.usermodel.Sheet

import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.actor.Props

import actors.ImportActor
import exceptions.{ImportFailed, UploadFailed, ExportFailed}
import models._
import pwguard.global.Globals.ExecutionContexts.Default._
import util.FileHelpers._
import util.EitherOptionHelpers._

import grizzled.io.util.withCloseable

import scala.annotation.tailrec
import scala.concurrent.{Promise, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** Supported import/export formats.
  */
object ImportExportFormat extends Enumeration {
  type ImportExportFormat = Value

  val CSV   = Value("csv")
  val Excel = Value("xlsx")
}

/** The names of the headers common to all entries, with some useful
  * extra stuff.
  */
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

  val AllCommonHeaders: Set[Value] = values.toSet

  val AllCommonHeaderNames: Set[String] = values.map { _.toString }
}

/** Represents an uploaded file.
  */
case class UploadedFile(name: String, contents: String, mimeType: String)

case class ImportData(csv:      File,
                      mappings: Map[String, String],
                      user:     User,
                      promise:  Promise[Int])


private case class MappedHeaders(nameHeader:  String,
                                 descHeader:  Option[String],
                                 loginHeader: Option[String],
                                 notesHeader: Option[String],
                                 pwHeader:    Option[String],
                                 urlHeader:   Option[String]) {
  val All = Set(Some(nameHeader),
                descHeader,
                loginHeader,
                notesHeader,
                pwHeader,
                urlHeader).flatten
}

/** Handles the logic of import and export, so it doesn't clutter up the
  * controller.
  */
object ImportExportService {

  import ImportExportFormat._
  import ImportFieldMapping._
  import dbservice.DAO.passwordEntryDAO

  val XSLXContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  val CSVContentType  = "text/csv"

  private val ExcelContentTypes = Set(
    "application/vnd.ms-excel",
    XSLXContentType
  )

  private val CSVContentTypes = Set(
    CSVContentType,
    "application/csv",
    "text/comma-separated-values"
  )

  private val logger = Logger("services.ImportExportService")

  private val importActor = Akka.system(current).actorOf(Props[ImportActor],
    "import-actor")

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------


  /** Create an export file, returning the file and the MIME type.
    *
    * @param user    the user whose entries are to be exported
    * @param format  the export format
    *
    * @return a `Future` containing a (`File`, mime-type) tuple
    */
  def createExportFile(user: User, format: ImportExportFormat):
    Future[(File, String)] = {

    for { entries      <- passwordEntryDAO.allForUser(user)
          fullEntries  <- passwordEntryDAO.fullEntries(entries)
          (file, mime) <- createDownload(fullEntries, user, format) }
    yield (file, mime)
  }

  /** Map an uploaded file of a specific format into a CSV file, if necessary,
    * and return the headers (so the user can map them).
    *
    * @param uploadedFile  the file
    *
    * @return A `Future` containing a (`File`, `Option[List[String]]`) tuple.
    *         The `File` is the mapped (temporary) CSV file, and the option,
    *         if defined, contains the headers from the file.
    */
  def mapUploadedImportFile(uploadedFile: UploadedFile) = {

    def header(r: CSVReader): Option[List[String]] = {
      r.readNext().flatMap { list =>
        list.filter(_.trim.length > 0) match {
          case Nil => None
          case l   => Some(l)
        }
      }
    }

    def decodeFile(): Future[File] = {
      import org.apache.commons.codec.binary.Base64
      import grizzled.file.{util => fileutil}
      import java.io.FileOutputStream

      for { bytes      <- Future { Base64.decodeBase64(uploadedFile.contents) }
            (_, _, ext) = fileutil.dirnameBasenameExtension(uploadedFile.name)
            file       <- createPseudoTempFile("pwguard", ext) }
      yield {
        withCloseable(new FileOutputStream(file)) { out =>
          out.write(bytes)
        }

        file
      }
    }

    for { f1           <- decodeFile()
          (f2, reader) <- getCSVReader(f1, uploadedFile.mimeType) }
    yield {
      withCloseable(reader) { r =>
        (f2, header(r))
      }
    }
  }

  /** Given a set of mappings and a previously mapped file, finish an export.
    *
    * @param csv      the CSV file
    * @param mappings the header mappings
    * @param user     user who owns the data in the CSV
    *
    * @return a `Future` of the number of entries imported
    */
  def importCSV(csv:      File,
                mappings: Map[String, String],
                user:     User): Future[Int] = {
    val p = Promise[Int]

    // Using an actor allows us to single-thread updates to the database,
    // avoiding locking issues. The actor calls back into this service
    // to do the actual work.

    importActor ! ImportData(csv, mappings, user, p)
    p.future
  }

  /** Return the format for a filename extension.
    *
    * @param ext  The extension, minus the "."
    *
    * @return a `Try` of the result
    */
  def mapFormatString(ext: String): Try[ImportExportFormat] = {
    ext match {
      case "xlsx" => Success(ImportExportFormat.Excel)
      case "csv"  => Success(ImportExportFormat.CSV)
      case _      => Failure(new ExportFailed(s"bad format: $ext"))
    }
  }

  /** Process a new import. This function is called from the import actor.
    *
    * @param data the import data
    */
  def processNewImport(data: ImportData): Unit = {

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

      processNextImportEntry(0,
                             reader.allWithHeaders(),
                             headers,
                             data.user) map { count =>
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

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def processNextImportEntry(count:   Int,
                                     rows:    List[Map[String, String]],
                                     headers: MappedHeaders,
                                     user:    User): Future[Int] = {
    rows match {
      case Nil => Future.successful(count)

      case row :: rest => loadOne(row, headers, user) flatMap { loaded =>
        processNextImportEntry(count + loaded, rest, headers, user)
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

    // Any other cells defined for this row constitute custom fields.

    @tailrec
    def handleExtraFields(fields: Set[PasswordEntryExtraField],
                          remainingExtraHeaders: List[String]):
      Set[PasswordEntryExtraField] = {

      remainingExtraHeaders match {
        case Nil => fields

        case name :: rest if row.get(name).isDefined => {
          // If the value is blank, ignore the field.
          val trimmedValue = row(name).trim
          val newFields = if (trimmedValue.length > 0) {
            fields + PasswordEntryExtraField(id = None,
                                             passwordEntryID = None,
                                             fieldName       = name,
                                             fieldValue      = trimmedValue)
          }
          else {
            fields
          }

          handleExtraFields(newFields, rest)
        }

        case name :: rest => fields
      }
    }

    // The row's key set is the set of headers for this row. Remove the
    // common headers, which we just processed. Anything left is a custom
    // field.
    val remainingHeaders = row.keySet -- headers.All
    val extraFields = handleExtraFields(Set.empty[PasswordEntryExtraField],
                                        remainingHeaders.toList)

    // Map to a full password entry.
    val fpw = entry.toFullEntry(extraFields)

    saveIfNew(fpw, headers.pwHeader.flatMap(row.get(_)), user) map { opt =>
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

  private def saveIfNew(entry:                FullPasswordEntry,
                        plaintextPasswordOpt: Option[String],
                        user:                 User):
    Future[Option[FullPasswordEntry]] = {

    val futureFuture: Future[Future[Option[FullPasswordEntry]]] =
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
          passwordEntryDAO.saveWithDependents(toSave) map { Some(_) }
        }
      }

    futureFuture.flatMap {f => f}
  }

  private def maybeEncryptPW(user: User, password: Option[String]):
    Future[Option[String]] = {

    password map { pw =>
      UserHelpers.encryptStoredPassword(user, pw) map { Some(_) }
    } getOrElse {
      Future.successful(noneT[String])
    }
  }

  private def getCSVReader(f: File, contentType: String):
    Future[(File, CSVReader)] = {

    if (ExcelContentTypes contains contentType)
      convertExcelToCSV(f).map { csv => (csv, CSVReader.open(csv)) }
    else if (CSVContentTypes contains contentType)
      Future { (f, CSVReader.open(f)) }
    else
      Future.failed(new ImportFailed(s"Unknown import file type: $contentType"))
  }

  private def unpackEntry(user: User, e: FullPasswordEntry):
    Future[Map[String, String]] = {

    val name                 = e.name
    val description          = e.description.getOrElse("")
    val loginID              = e.loginID.getOrElse("")
    val notes                = e.notes.getOrElse("")
    val url                  = e.url.getOrElse("")
    val encryptedPasswordOpt = e.encryptedPassword

    def getEncryptedPassword(): Future[String] = {
      encryptedPasswordOpt map { epw =>
        UserHelpers.decryptStoredPassword(user, epw)
      } getOrElse {
        Future.successful("")
      }
    }

    @tailrec
    def addCustomFields(fields: List[PasswordEntryExtraField],
                        m:      Map[String, String]):
      Map[String, String] = {

      fields match {
        case Nil => m
        case e :: rest => {
          val newMap = m + (e.fieldName -> e.fieldValue)
          addCustomFields(rest, newMap)
        }
      }
    }

    for { epw <- getEncryptedPassword() }
    yield {
      val initialMap = Map(ImportFieldMapping.Name.toString        -> name,
                           ImportFieldMapping.Password.toString    -> epw,
                           ImportFieldMapping.Description.toString -> description,
                           ImportFieldMapping.Login.toString       -> loginID,
                           ImportFieldMapping.URL.toString         -> url,
                           ImportFieldMapping.Notes.toString       -> notes)
      addCustomFields(e.extraFields.toList, initialMap)
    }
  }


  private def createDownload(entries: Set[FullPasswordEntry],
                             user:    User,
                             format:  ImportExportFormat):
    Future[(File, String)] = {

    createPseudoTempFile("pwguard", format.toString) flatMap { out =>

      val entryFutures = entries.map { unpackEntry(user, _) }.toSeq
      for { seqOfFutures <- Future.sequence(entryFutures) }
      yield (out, seqOfFutures)

    } flatMap { case (out, entries) =>

      format match {
        case Excel => writeExcel(out, entries).map { (_, XSLXContentType) }
        case CSV   => writeCSV(out, entries).map { (_, CSVContentType) }
        case _     => throw new ExportFailed("Unknown format")
      }
    }
  }

  private def convertExcelToCSV(f: File): Future[File] = {
    import org.apache.poi.ss.usermodel.WorkbookFactory
    import scala.collection.JavaConversions.asScalaIterator

    logger.debug(s"Converting ${f.getName} to CSV.")

    def getWorksheet(): Future[Sheet] = {
      Future {
        val wb = WorkbookFactory.create(f)
        wb.getNumberOfSheets match {
          case 0 => throw new UploadFailed(s"No worksheets in ${f.getName}")
          case 1 => wb.getSheetAt(0)
          case n => {
            logger.warn(s"$n worksheets in ${f.getName}. Ignoring extras.")
            wb.getSheetAt(0)
          }
        }
      }
    }

    for { sheet   <- getWorksheet()
          csvFile <- createPseudoTempFile("pwguard", ".csv") }
    yield {
      val writer = CSVWriter.open(csvFile)
      val reBackslash = """\\""".r

      for (row <- sheet.iterator) {
        // Can't use the row.cellIterator call, because it only returns
        // the non-null cells.
        val firstCellNum = row.getFirstCellNum
        if (firstCellNum >= 0) {
          val lastCellNum = row.getLastCellNum
          val cells = for (i <- firstCellNum to lastCellNum) yield {
            val cell = Option(row.getCell(i))
            // Map to string and escape any embedded backslashes.
            cell.map { c =>
              val s = c.toString
              reBackslash.replaceAllIn(s, """\\\\""")
            }.
            getOrElse("")
          }

          writer.writeRow(cells)
        }
      }

      writer.close()
      f.delete()
      csvFile.deleteOnExit()
      csvFile
    }
  }


  private def writeExcel(out: File, entries: Seq[Map[String, String]]):
    Future[File] = {

    import java.io.FileOutputStream
    import org.apache.poi.xssf.usermodel.XSSFWorkbook

    Future {
      withCloseable(new FileOutputStream(out)) { fOut =>
        val wb = new XSSFWorkbook
        val sheet = wb.createSheet("Passwords")

        val headers = collectHeaders(entries)

        // Write the header.
        val font = wb.createFont()
        font.setBold(true)
        val style = wb.createCellStyle()
        style.setFont(font)

        val row = sheet.createRow(0.asInstanceOf[Short])
        for ((cellValue, i) <- headers.zipWithIndex) {
          val cell = row.createCell(i)
          cell.setCellValue(cellValue)
          cell.setCellStyle(style)
        }

        // Now the data.
        for { (rowMap, rI)  <- entries.zipWithIndex
              rowNum         = rI + 1
              row            = sheet.createRow(rowNum.asInstanceOf[Short])
              (name, cI)    <- headers.zipWithIndex } {
          val cellNum = cI + 1
          val cell    = row.createCell(cI.asInstanceOf[Short])
          cell.setCellValue(rowMap.getOrElse(name, ""))
        }

        wb.write(fOut)
        out
      }
    }
  }

  private def writeCSV(out: File, entries: Seq[Map[String, String]]):
    Future[File] = {

    Future {
      withCloseable(new BufferedWriter(
        new OutputStreamWriter(
          new FileOutputStream(out), "UTF-8"))) { fOut =>
        withCloseable(CSVWriter.open(fOut)) { csv =>

          val headers = collectHeaders(entries)

          // Write the header.
          csv.writeRow(headers)

          // Now the data.
          for ( rowMap <- entries ) {
            val row = headers.map { i => rowMap.getOrElse(i.toString, "") }
                             .toList
            csv.writeRow(row)
          }

          out
        }
      }
    }
  }

  private def collectHeaders(entries: Seq[Map[String,String]]): Seq[String] = {
    val headerSet = entries.flatMap { m => m.keySet }.toSet
    val extraHeaders = headerSet -- ImportFieldMapping.AllCommonHeaderNames

    // Put them together, required headers first.
    ImportFieldMapping.valuesInOrder.map { _.toString } ++ extraHeaders.toSeq
  }

}
