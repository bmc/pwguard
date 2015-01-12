package services

import java.io.{OutputStreamWriter, FileOutputStream, BufferedWriter, File}

import com.github.tototoshi.csv.{CSVReader, CSVWriter}
import org.apache.poi.ss.usermodel.Sheet

import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.actor.Props

import actors.{ImportData, ImportFieldMapping, ImportActor}
import actors.ImportFieldMapping.ImportFieldMapping
import exceptions.{ImportFailed, UploadFailed, ExportFailed}
import models.{UserHelpers, PasswordEntry, User}
import pwguard.global.Globals.ExecutionContexts.Default._
import util.FileHelpers._

import grizzled.io.util.withCloseable

import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success, Try}

/** Supported import/export formats.
  */
object ImportExportFormat extends Enumeration {
  type ImportExportFormat = Value

  val CSV   = Value("csv")
  val Excel = Value("xlsx")
}

/** Represents an uploaded file.
  */
case class UploadedFile(name: String, contents: String, mimeType: String)

/** Handles the logic of import and export, so it doesn't clutter up the
  * controller.
  */
object ImportExportService {

  import ImportExportFormat._
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

    for { seq          <- passwordEntryDAO.allForUser(user)
          (file, mime) <- createDownload(seq, user, format) }
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
    // avoiding locking issues.

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

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def getCSVReader(f: File, contentType: String):
  Future[(File, CSVReader)] = {

    if (ExcelContentTypes contains contentType)
      convertExcelToCSV(f).map { csv => (csv, CSVReader.open(csv)) }
    else if (CSVContentTypes contains contentType)
      Future { (f, CSVReader.open(f)) }
    else
      Future.failed(new ImportFailed(s"Unknown import file type: $contentType"))
  }

  private def unpackEntry(user: User, e: PasswordEntry):
  Future[Map[ImportFieldMapping.Value, String]] = {

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
      Map(ImportFieldMapping.Name        -> name,
        ImportFieldMapping.Password    -> pwString,
        ImportFieldMapping.Description -> description,
        ImportFieldMapping.Login       -> loginID,
        ImportFieldMapping.URL         -> url,
        ImportFieldMapping.Notes       -> notes)
    }
  }


  private def createDownload(entries: Set[PasswordEntry],
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


  private def writeExcel(out:     File,
                         entries: Seq[Map[ImportFieldMapping, String]]):
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
        for ((cellValue, i) <- ImportFieldMapping.valuesInOrder.zipWithIndex) {
          val cell = row.createCell(i)
          cell.setCellValue(cellValue.toString)
          cell.setCellStyle(style)
        }

        // Now the data.
        for { (rowMap, rI)  <- entries.zipWithIndex
              rowNum         = rI + 1
              row            = sheet.createRow(rowNum.asInstanceOf[Short])
              (name, cI)    <- ImportFieldMapping.valuesInOrder.zipWithIndex } {
          val cellNum = cI + 1
          val cell    = row.createCell(cI.asInstanceOf[Short])
          cell.setCellValue(rowMap.getOrElse(name, ""))
        }

        wb.write(fOut)
        out
      }
    }
  }

  private def writeCSV(out:     File,
                       entries: Seq[Map[ImportFieldMapping, String]]):
  Future[File] = {

    Future {
      withCloseable(new BufferedWriter(
        new OutputStreamWriter(
          new FileOutputStream(out), "UTF-8"))) { fOut =>
        withCloseable(CSVWriter.open(fOut)) { csv =>

          // Write the header.
          csv.writeRow(ImportFieldMapping.valuesInOrder.map(_.toString).toList)

          // Now the data.
          for ( rowMap <- entries ) {
            val row = ImportFieldMapping.valuesInOrder
              .map { rowMap.getOrElse(_, "") }
              .toList
            csv.writeRow(row)
          }

          out
        }
      }
    }
  }

}


