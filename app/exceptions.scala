package exceptions

class UploadFailed(msg: String) extends Exception(msg)
class ImportFailed(msg: String) extends Exception(msg)
class ExportFailed(msg: String) extends Exception(msg)
class SaveFailed(msg: String) extends Exception(msg)
class DAOException(msg: String) extends Exception(msg)
