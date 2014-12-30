package pwguard.global

import play.api.mvc.WithFilters
import play.api.{Logger, GlobalSettings, Application}
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.filters.csrf._
import services.UserAgentDecoder
import scala.concurrent.ExecutionContext
import scala.language.postfixOps;
import scala.slick.driver.{
  MySQLDriver,
  PostgresDriver,
  SQLiteDriver,
  JdbcProfile
}


/** Global object, for startup. DON'T IMPORT THIS! Import Globals, instead.
  */
object Global extends WithFilters(CSRFFilter()) with GlobalSettings {
    // No choice but to use vars here.
  private[global] var _dal: Option[dbservice.DAL] = None
  private[global] var _db: Option[scala.slick.jdbc.JdbcBackend#Database] = None
  private[global] var _uaService: Option[UserAgentDecoder] = None

  private val logger = Logger("pwguard.global.init")

  override def onStart(app: Application) {
    super.onStart(app)
    initDB(app)
    initServices(app)
  }

  private def initDB(app: Application) {
    import scala.slick.jdbc.JdbcBackend.Database
    import dbservice.DAL

    val cfg = app.configuration
    val useDB = cfg.getString("use_db").getOrElse("default")
    val cfgPrefix = s"db.$useDB"
    val driver = cfg.getString(s"$cfgPrefix.driver")
    val url = cfg.getString(s"$cfgPrefix.url")
    val user = cfg.getString(s"$cfgPrefix.username")
    val password = cfg.getString(s"$cfgPrefix.password")

    if (Seq(driver, url).flatMap { t => t }.length != 2) {
      sys.error("Missing 'url' and/or 'driver' in config section $cfgPrefix")
    }

    val (dal, db) = driver.get match {
      case "org.postgresql.Driver" => {
        (new DAL(PostgresDriver),
          Database.forURL(url.get,
                          driver = driver.get,
                          user = user.get,
                          password = password.get)
        )
      }

      case "com.mysql.jdbc.Driver" => {
        (new DAL(MySQLDriver),
          Database.forURL(url.get,
                          driver = driver.get,
                          user = user.get,
                          password = password.get)
        )
      }

      case "org.sqlite.JDBC" => {
        (new DAL(SQLiteDriver),
          Database.forURL(url.get,
                          driver = driver.get,
                          user = user.get,
                          password = password.get)
        )
      }

      case _ => {
        sys.error(s"Unsupported database driver: ${driver.get}")
      }
    }

    _dal = Some(dal)
    _db = Some(db)
  }

  def initServices(app: Application) {
    val akkaSystem = Akka.system(app)
    _uaService = Some(new UserAgentDecoder(akkaSystem))
  }
}

/** MainController globals.
  */
object Globals {
  val mainLogger = Logger("pwguard.application")

  lazy val DAL                     = Global._dal.get
  lazy val DB                      = Global._db.get
  lazy val UserAgentDecoderService = Global._uaService.get

  object ExecutionContexts {
    import play.api.libs.concurrent.Execution.{Implicits ⇒ PlayImplicits}

    object Default {
      implicit val default: ExecutionContext = PlayImplicits.defaultContext
    }

    object DB {
      implicit val dbContext: ExecutionContext =
        Akka.system.dispatchers.lookup("contexts.db")
    }
  }
}
