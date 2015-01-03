package controllers

import akka.actor.Props
import dbservice.DAO
import models.{UserHelpers, User, PasswordEntry}
import org.apache.poi.ss.usermodel.Sheet
import play.api.libs.concurrent.Akka
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
import actors.{ImportFieldMapping, ImportData, ImportActor}
import exceptions._

import scala.concurrent.{Promise, Future}
import scala.util.control.NonFatal

object ImportExportController extends BaseController {

  override val logger = Logger("pwguard.controllers.ImportExportController")

  import DAO.passwordEntryDAO
  import ImportFieldMapping._

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

  private val importActor = Akka.system(current).actorOf(Props[ImportActor],
                                                         "import-actor")

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def exportData(format: String) = SecuredAction { authReq =>

    def unpackEntry(user: User, e: PasswordEntry):
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

    def createDownload(entries: Set[PasswordEntry], user: User):
      Future[(File, String)] = {

      createFile("pwguard", format) flatMap { out =>

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

  def importDataUpload = SecuredJSONAction { authReq =>
    implicit val request = authReq.request

    case class UploadedFile(name: String, data: String, mimeType: String)

    val json = request.body

    val optData = for { name <- (json \ "filename").asOpt[String]
                        data <- (json \ "contents").asOpt[String]
                        mime <- (json \ "mimeType").asOpt[String] }
                  yield UploadedFile(name, data, mime)

    optData map { uploaded =>

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

        def extension(filename: String): String = {
          val (_, _, extension) = fileutil.dirnameBasenameExtension(filename)
          extension
        }

        for { bytes <- Future { Base64.decodeBase64(uploaded.data) }
              ext    = extension(uploaded.name)
              file  <- createFile("pwguard", ext) }
        yield {
          withCloseable(new FileOutputStream(file)) { out =>
            out.write(bytes)
          }

          file
        }
      }

      for { f1           <- decodeFile()
            (f2, reader) <- getCSVReader(f1, uploaded.mimeType) }
      yield {
        val jsonOpt = for { h  <- header(reader) }
        yield {
          Cache.set(FileCacheKey, f2.getPath)
          Ok(
            Json.obj(
              "headers" -> h,
              "fields"  -> ImportFieldMapping.values.map { field =>
                Json.obj("name" -> field.toString,
                         "required" -> ImportFieldMapping.isRequired(field))
              }
            )
          )
        }

        jsonOpt.getOrElse {
          throw new UploadFailed("Empty file.")
        }
      }
    } getOrElse {
      Future.failed(new UploadFailed("Incomplete file upload."))

    } recover {
      case NonFatal(e) => {
        logger.error("Error preparing import", e)
        BadRequest(jsonError(e))
      }
    }
  }

  def completeImport = SecuredJSONAction { authReq =>
    def getFile(): Future[File] = {
      Future {
        val path = Cache.getAs[String](FileCacheKey).getOrElse {
          throw new ImportFailed("No previously uploaded file.")
        }
        val f = new File(path)
        f
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

    // Create a promise. The import actor will complete it.
    val p = Promise[Int]

    // Get the mappings, find the uploaded file, and send the data over to
    // the actor. This approach allows the actor to single-thread updates
    // to the database, avoiding locking issues.
    val f =
      for { file     <- getFile()
            mappings <- getMappings() }
      yield {
        importActor ! ImportData(file, mappings, authReq.user, p)
      }

    p.future map  { total =>
      // Get rid of the entries that weren't saved because they weren't
      // new.
      Ok(Json.obj("total" -> total))

    } recover {
      case NonFatal(e) => {
        logger.error("Cannot complete import", e)
        BadRequest(jsonError(e))
      }
    } andThen {
      case _ => {
        getFile() map { f =>
          f.delete()
          Cache.remove(FileCacheKey)
        }
      }
    }
  }

  // -------------------------------------------------------------------------
  // Private Methods
  // -------------------------------------------------------------------------

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

  private def getCSVReader(f: File, contentType: String):
    Future[(File, CSVReader)] = {

    if (ExcelContentTypes contains contentType)
      convertExcelToCSV(f).map { csv => (csv, CSVReader.open(csv)) }
    else if (CSVContentTypes contains contentType)
      Future { (f, CSVReader.open(f)) }
    else
      Future.failed(new ImportFailed(s"Unknown import file type: $contentType"))
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
          csvFile <- createFile("pwguard", ".csv") }
    yield {
      val writer = CSVWriter.open(csvFile)

      for (row <- sheet.iterator) {
        // Can't use the row.cellIterator call, because it only returns
        // the non-null cells.
        val firstCellNum = row.getFirstCellNum
        if (firstCellNum >= 0) {
          val lastCellNum = row.getLastCellNum
          val cells = for (i <- firstCellNum to lastCellNum) yield {
            val cell = Option(row.getCell(i))
            cell.map { _.toString }.getOrElse("")
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
  private def createFile(prefix: String, suffix: String): Future[File] = {
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
