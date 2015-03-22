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
import scala.xml

object ImportMimeTypes {
  val XSLXContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  val CSVContentType  = "text/csv"
  val XMLContentType  = "text/xml"
}

/** Supported import/export formats.
  */
object ImportExportFormat extends Enumeration {
  type ImportExportFormat = Value

  val CSV   = Value("csv")
  val Excel = Value("xlsx")
  val XML   = Value("xml")
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
  val Keywords    = Value
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
case class UploadedFile(name: String, file: File, mimeType: String) {
  val fileFormat = {
    mimeType match {
      case ImportMimeTypes.XMLContentType  => ImportExportFormat.XML
      case ImportMimeTypes.XSLXContentType => ImportExportFormat.Excel
      case ImportMimeTypes.CSVContentType  => ImportExportFormat.CSV
    }
  }
}

case class ImportCSVData(csv:      File,
                         mappings: Map[String, String],
                         user:     User,
                         promise:  Promise[Int])

case class ImportXMLData(xml: File, user: User, promise: Promise[Int])

private case class MappedHeaders(nameHeader:     String,
                                 descHeader:     Option[String],
                                 loginHeader:    Option[String],
                                 notesHeader:    Option[String],
                                 keywordsHeader: Option[String],
                                 pwHeader:       Option[String],
                                 urlHeader:      Option[String]) {
  val All = Set(Some(nameHeader),
                descHeader,
                loginHeader,
                notesHeader,
                keywordsHeader,
                pwHeader,
                urlHeader).flatten
}

/** Handles the logic of import and export, so it doesn't clutter up the
  * controller.
  */
object ImportExportService {

  import ImportMimeTypes._
  import ImportExportFormat._
  import ImportFieldMapping._
  import dbservice.DAO.passwordEntryDAO

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

  private val SplitKeywords = """[,\s]+""".r

  private val SecurityQuestionHeaderPrefix = "Security Question"

  private val LCSecurityQuestionHeaderPrefix = SecurityQuestionHeaderPrefix.toLowerCase

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

    for { (f1, reader) <- getCSVReader(uploadedFile.file, uploadedFile.mimeType) }
    yield {
      withCloseable(reader) { r =>
        (f1, header(r))
      }
    }
  }

  /** Given an XML file of password entries and a user, import the XML data
    * into the database.
    *
    * @param xml      the XML file
    * @param user     user who owns the data in the CSV
    *
    * @return a `Future` of the number of entries imported
    */
  def importXML(xml: File, user: User): Future[Int] = {
    val p = Promise[Int]

    // Using an actor allows us to single-thread updates to the database,
    // avoiding locking issues. The actor calls back into this service
    // to do the actual work.

    importActor ! ImportXMLData(xml, user, p)
    p.future
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

    importActor ! ImportCSVData(csv, mappings, user, p)
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
      case "xml"  => Success(ImportExportFormat.XML)
      case _      => Failure(new ExportFailed(s"bad format: $ext"))
    }
  }

  /** Process a new XML import. This function is called from the import actor.
    *
    * @param data the import data
    */
  def processNewXMLImport(data: ImportXMLData): Unit = {
    val root = xml.XML.loadFile(data.xml)

    loadXML(root, data.user) map { count =>
      data.promise.complete(Success(count))
    } recover {
      case NonFatal(e) => {
        logger.error("Error processing XML import", e)
        data.promise.failure(e)
      }
    }
  }

  /** Process a CSV new import. This function is called from the import actor.
    *
    * @param data the import data
    */
  def processNewCSVImport(data: ImportCSVData): Unit = {

    Try {
      logger.debug(s"Handling new import: $data")

      val reader      = CSVReader.open(data.csv)
      val mappings    = data.mappings

      val headers = MappedHeaders(
        nameHeader     = mappingFor(ImportFieldMapping.Name, mappings).get,
        descHeader     = mappingFor(ImportFieldMapping.Description, mappings),
        loginHeader    = mappingFor(ImportFieldMapping.Login, mappings),
        notesHeader    = mappingFor(ImportFieldMapping.Notes, mappings),
        pwHeader       = mappingFor(ImportFieldMapping.Password, mappings),
        keywordsHeader = mappingFor(ImportFieldMapping.Keywords, mappings),
        urlHeader      = mappingFor(ImportFieldMapping.URL, mappings)
      )

      // processNext() is recursive, allowing us to chain an arbitrary
      // number of futures, one after another, each one processing a single
      // row from the spreadsheet. However, a large enough spreadsheet will
      // blow the stack. The number is in the thousands, typically, but it's
      // still not impossible to hit it.

      processNextCSVEntry(0,
                          reader.allWithHeaders(),
                          headers,
                          data.user) map { count =>
        // All done. Complete the promise.
        data.promise.complete(Success(count))
      }
    } recover {
      case NonFatal(e) => {
        logger.error("Error processing spreadsheet import", e)
        data.promise.failure(e)
      }
    }

  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def processNextCSVEntry(count:   Int,
                                  rows:    List[Map[String, String]],
                                  headers: MappedHeaders,
                                  user:    User): Future[Int] = {
    rows match {
      case Nil => Future.successful(count)

      case row :: rest => loadOneCSVRow(row, headers, user) flatMap { loaded =>
        processNextCSVEntry(count + loaded, rest, headers, user)
      }
    }
  }

  private def loadOneCSVRow(row:     Map[String, String],
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
    def handleExtraFields(fields:       Set[PasswordEntryExtraField],
                          extraHeaders: List[String]):
      Set[PasswordEntryExtraField] = {

      extraHeaders match {
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

        case name :: rest => handleExtraFields(fields, rest)
      }
    }

    def handleKeywords(keywordString: String): Set[PasswordEntryKeyword] = {
      SplitKeywords.split(keywordString).map { s =>
        PasswordEntryKeyword(id = None, passwordEntryID = None, keyword = s)
      }.toSet
    }

    val REQuestionSplit = """\s*\?\s*""".r

    @tailrec
    def handleSecurityQuestions(questions: Set[PasswordEntrySecurityQuestion],
                                headers:   List[String]):
      Set[PasswordEntrySecurityQuestion] = {

      def makeQuestion(q: String, a: String) = {
        PasswordEntrySecurityQuestion(id              = None,
                                      passwordEntryID = None,
                                      question        = q,
                                      answer          = a)
      }

      def splitQuestion(s: String): Option[(String, String)] = {
        REQuestionSplit.split(s) match {
          case Array("")             => None
          case Array(q, a)           => Some((s"$q?", a))
          case Array(q, tokens @ _*) => Some((s"$q?", tokens.mkString(" ")))
        }
      }

      headers match {
        case Nil => questions

        case header :: rest if row.get(header).isDefined => {
          // Split the question string on "?", so we get a question and answer.
          // If the split fails, skip the field. Here's one valid reason to
          // match on an Option: It allows tail recursion. Using map() doesn't.
          splitQuestion(row(header)) match {
            case Some((q, a)) => {
              handleSecurityQuestions(questions + makeQuestion(q, a), rest)
            }
            case _ => handleSecurityQuestions(questions, rest)
          }
        }
      }
    }

    // The row's key set is the set of headers for this row. Remove the
    // common headers, which we just processed. Anything left is a custom
    // field or security question.
    val remainingHeaders = row.keySet -- headers.All

    val securityQuestionHeaders = remainingHeaders.filter {
      _.toLowerCase.startsWith(LCSecurityQuestionHeaderPrefix)
    }

    val extraHeaders = remainingHeaders -- securityQuestionHeaders

    val extras = handleExtraFields(Set.empty[PasswordEntryExtraField],
                                   extraHeaders.toList)
    val questions = handleSecurityQuestions(Set.empty[PasswordEntrySecurityQuestion],
                                            securityQuestionHeaders.toList)

    val keywordString = headers.keywordsHeader.flatMap(row.get(_)).getOrElse("")
    val keywords = handleKeywords(keywordString)

    // Map to a full password entry.
    val fpw = entry.toFullEntry(extras            = extras,
                                keywords          = keywords,
                                securityQuestions = questions)

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

  private def unpackEntryToSpreadsheetMap(user: User, e: FullPasswordEntry):
    Future[Map[String, String]] = {

    val name                 = e.name
    val description          = e.description.getOrElse("")
    val loginID              = e.loginID.getOrElse("")
    val notes                = e.notes.getOrElse("")
    val url                  = e.url.getOrElse("")
    val encryptedPasswordOpt = e.encryptedPassword
    val keywords             = e.keywords map { _.keyword } mkString (",")

    def decryptPassword(): Future[String] = {
      UserHelpers.decryptStoredPasswordOpt(user, encryptedPasswordOpt) map {
        _.getOrElse("")
      }
    }

    @tailrec
    def addExtraFields(fields: List[PasswordEntryExtraField],
                       m:      Map[String, String]):
      Map[String, String] = {

      fields match {
        case Nil => m
        case e :: rest => {
          val newMap = m + (e.fieldName -> e.fieldValue)
          addExtraFields(rest, newMap)
        }
      }
    }

    @tailrec
    def addSecurityQuestions(questions: List[PasswordEntrySecurityQuestion],
                             m:         Map[String, String],
                             n:         Int):
      Map[String, String] = {

      /* It's possible for security questions to be encoded as extra fields.
        * This function ensures that there's no inadvertent header clash.
        * It finds the next available (unused) header composed of the
        * security question prefix and a number.
        */
      @tailrec
      def headerAndIndex(n: Int): (String, Int) = {
        val header = s"${SecurityQuestionHeaderPrefix} $n"
        val candidates = m.keySet map {
          _.toLowerCase
        } filter {
          _.startsWith(LCSecurityQuestionHeaderPrefix)
        }

        if (candidates.contains(header.toLowerCase))
          headerAndIndex(n + 1)
        else
          (header, n)
      }

      questions match {
        case Nil => m
        case q :: rest => {
          val (header, index) = headerAndIndex(n)
          val newMap = m + (header -> s"${q.question} ${q.answer}")
          addSecurityQuestions(rest, newMap, index)
        }
      }
    }

    for { epw <- decryptPassword() }
    yield {
      val initialMap = Map(ImportFieldMapping.Name.toString        -> name,
                           ImportFieldMapping.Password.toString    -> epw,
                           ImportFieldMapping.Description.toString -> description,
                           ImportFieldMapping.Login.toString       -> loginID,
                           ImportFieldMapping.URL.toString         -> url,
                           ImportFieldMapping.Keywords.toString    -> keywords,
                           ImportFieldMapping.Notes.toString       -> notes)
      val m = addExtraFields(e.extraFields.toList, initialMap)
      val m2 = addSecurityQuestions(e.securityQuestions.toList, m, 1)
      m2
    }
  }

  private def createDownload(entries: Set[FullPasswordEntry],
                             user:    User,
                             format:  ImportExportFormat):
    Future[(File, String)] = {

    createPseudoTempFile("pwguard", format.toString) flatMap { out =>

      if (format == ImportExportFormat.XML) {
        writeXML(out, entries, user) map { (_, XMLContentType) }
      }
      else {
        val entryFutures = entries.map { unpackEntryToSpreadsheetMap(user, _) }.toSeq
        for { seqOfFutures <- Future.sequence(entryFutures) }
        yield (out, seqOfFutures)

      } flatMap { case (out, entries) =>
        format match {
          case Excel => writeExcel(out, entries).map { (_, XSLXContentType) }
          case CSV   => writeCSV(out, entries).map { (_, CSVContentType) }
          case _     => throw new ExportFailed("Unknown spreadsheet format")
        }
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

    // Extra headers includes security questions.
    val extraHeaders = headerSet -- ImportFieldMapping.AllCommonHeaderNames

    // Put them together, required headers first.
    ImportFieldMapping.valuesInOrder.map { _.toString } ++
      extraHeaders.toSeq.sorted
  }

  private def loadXML(root: xml.Elem, user: User): Future[Int] = {
    // Okay to throw exceptions here. The caller will trap them.

    def nodeName(node: xml.Node) = node.nameToString(new StringBuilder).toString

    def requiredAttribute(e: xml.Node, elemName: String, attrName: String): String = {
      optionalAttribute(e, elemName, attrName) getOrElse {
        throw new ImportFailed(s"${elemName}: ${attrName} attribute is required.")
      }
    }

    def optionalAttribute(e: xml.Node, elemName: String, attrName: String):
      Option[String] = {
      (e \ s"@${attrName}").headOption.map { attrNode => attrNode.text }
    }

    if (nodeName(root) != "pwguard-data")
      throw new ImportFailed("Root XML element must be <pwguard-data>.")

    def parseExtras(entry: xml.Node): Set[PasswordEntryExtraField] = {
      import grizzled.string.{util => stringutil}

      (entry \ "extra-fields" \ "extra-field").map { node: xml.Node =>
        val fieldName  = requiredAttribute(node, "extra-field", "name")
        val fieldValue = requiredAttribute(node, "extra-field", "value")
        val s = optionalAttribute(node, "extra-field", "isPassword")
        val isPW = optionalAttribute(node, "extra-field", "isPassword") map { s =>
          stringutil.strToBoolean(s) match {
            case Left(error) => throw new ImportFailed(error)
            case Right(b)    => b
          }
        } getOrElse (false)

        PasswordEntryExtraField(id              = None,
                                passwordEntryID = None,
                                fieldName       = fieldName,
                                fieldValue      = fieldValue,
                                isPassword      = isPW)
      }.
      toSet
    }

    def parseSecurityQuestions(entry: xml.Node):
      Set[PasswordEntrySecurityQuestion] = {

      (entry \ "security-questions" \ "security-question").map { node: xml.Node =>
        val question = requiredAttribute(node, "security-question", "question")
        val answer   = requiredAttribute(node, "security-question", "answer")

        PasswordEntrySecurityQuestion(id              = None,
                                      passwordEntryID = None,
                                      question        = question,
                                      answer          = answer)
      }.
      toSet
    }

    def parseKeywords(entry: xml.Node): Set[PasswordEntryKeyword] = {
      (entry \ "keywords" \ "keyword").flatMap { node: xml.Node =>
        val s = node.text
        if (s.isEmpty)
          None
        else
          Some(PasswordEntryKeyword(id              = None,
                                    passwordEntryID = None,
                                    keyword         = s))
      }.
      toSet
    }

    def loadEntry(node: xml.Node): (FullPasswordEntry, Option[String]) = {
      val name        = requiredAttribute(node, "password-entry", "name")
      val description = optionalAttribute(node, "password-entry", "description")
      val url         = optionalAttribute(node, "password-entry", "url")
      val loginID     = optionalAttribute(node, "password-entry", "loginID")
      val password    = optionalAttribute(node, "password-entry", "password")
      val notes       = optionalAttribute(node, "password-entry", "notes")

      val entry = FullPasswordEntry(
        id                = None,
        userID            = user.id.get,
        name              = name,
        description       = description,
        loginID           = loginID,
        encryptedPassword = None,
        url               = url,
        notes             = notes,
        keywords          = parseKeywords(node),
        extraFields       = parseExtras(node),
        securityQuestions = parseSecurityQuestions(node)
      )

      (entry, password)
    }

    val entries = (root \ "password-entry").map { node => loadEntry(node) }

    // Use foldLeft in combination with flatMap to serialize the saves, so
    // the database doesn't lock. See comments to this StackOverflow answer:
    // http://stackoverflow.com/a/20417884/53495

    entries.foldLeft(Future.successful(0)) {
      case (future, (entry, pw)) => {
        future.flatMap { n =>
          saveIfNew(entry, pw, user).map { epwOpt =>
            n + epwOpt.map(_ => 1).getOrElse(0)
          }
        }
      }
    }
  }

  private def writeXML(out: File, entries: Set[FullPasswordEntry], user: User):
    Future[File] = {

    case class EntryWithPassword(entry:    FullPasswordEntry,
                                 password: Option[String])




    def optionalXML[T](i: Set[T])(code: => xml.NodeSeq): xml.NodeSeq = {
      if (i.isEmpty)
        new xml.NodeBuffer()
      else
        code
    }

    def convertEntry(ewp: EntryWithPassword): xml.Elem = {
      val keywords = optionalXML(ewp.entry.keywords) {
        <keywords>
          { ewp.entry.keywords.map { k => <keyword>{k.keyword}</keyword> } }
        </keywords>
      }

      val extras = optionalXML(ewp.entry.extraFields) {
        <extra-fields>
          {
          ewp.entry.extraFields.map { f =>
              <extra-field name={f.fieldName} value={f.fieldValue}
                           is-password={f.isPassword.toString}/>
          }
          }
        </extra-fields>
      }

      val securityQuestions = optionalXML(ewp.entry.securityQuestions) {
        <security-questions>
          {
          ewp.entry.securityQuestions.map { q =>
              <security-question question={q.question} answer={q.answer}/>
          }
          }
        </security-questions>
      }

      <password-entry name={ewp.entry.name}
                      url={ewp.entry.url.getOrElse("")}
                      login-id={ewp.entry.loginID.getOrElse("")}
                      password={ewp.password.getOrElse("")}>
        <description>{ewp.entry.description.getOrElse("")}</description>
        <notes>{ewp.entry.notes.getOrElse("")}</notes>
        {keywords}
        {extras}
        {securityQuestions}
      </password-entry>
    }

    def convertEntries(entries:  List[EntryWithPassword],
                       xmlSoFar: xml.NodeBuffer): Seq[xml.Node] = {
      entries match {
        case Nil => xmlSoFar.toSeq

        case entry :: rest => {
          xmlSoFar += convertEntry(entry)
          convertEntries(rest, xmlSoFar)
        }
      }
    }

    Future.sequence {
      import UserHelpers._

      entries.toList.map { e =>
        decryptStoredPasswordOpt(user, e.encryptedPassword) map { pwOpt =>
          EntryWithPassword(e, pwOpt)
        }
      }
    } map { entriesWithPasswords =>

      val buf = new xml.NodeBuffer()
      val root = <pwguard-data>
                   {convertEntries(entriesWithPasswords, buf)}
                 </pwguard-data>
      val pp = new xml.PrettyPrinter(width = 79, step = 2)

      withCloseable(new BufferedWriter(
        new OutputStreamWriter(
          new FileOutputStream(out), "UTF-8"))) { fOut =>
        fOut.write(pp.format(root))
      }

      out
    }
  }

}
