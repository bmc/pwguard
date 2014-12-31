# PWGuard - A password vault

## Overview

PWGuard implements a simple web-based password vault, using the
[Play Framework][] and [AngularJS][]. PWGuard allows for
a relatively secure deployment, by providing the following capabilities:

* By default, it uses a [SQLite][] database. Since SQLite confines its data to
  a single file, you can easily locate that file on an encrypted volume.
* By putting an SSL-aware web server, such as [Nginx][], in front of the
  running PWGuard server, you can encrypt the data in-transit. By default,
  PWGuard will refuse to do anything unless it detects that it is running
  behind an SSL gateway.
* User login passwords are one-way encrypted via [BCrypt][JBCrypt].
* Stored passwords are encrypted with a user-specific (symmetric) key,
  which is kept in the database. **WARNING**: This strategy protects
  stored passwords from unsophisticated attacks, but anyone who obtains the
  SQLite database can use this source code to figure out how to decrypt them.
  **Protect your database.**
* PWGuard uses [Twitter Bootstrap][bootstrap] and some custom Javascript to
  (try to) ensure that the application works well on mobile devices, as well
  as desktop browsers.
* PWGuard allows you to export your stored data as a CSV or Excel spreadsheet,
  and it supports re-importing an exported spreadsheet. This capability allows
  individual users to create backups of their password data, even if they
  don't have access to the SQLite database file.

## Purpose

This application serves two purposes:

1. It provides me a convenient way to save various passwords and related
   data, search for them, and access them remotely and securely (e.g., from my
   phone).

2. It's a testbed application, for both Play and AngularJS. I use Play and
   AngularJS in my consulting work, and I teach Play and AngularJS courses
   for [NewCircle](http://thenewcircle.com/). Having a well-defined,
   relatively small, open source application that uses both technologies
   allows me to experiment with new technologies and with changes in Play
   and AngularJS, without affecting my clients.

## Deploying

Deploying PWGuard is straighforward, though there's some initial setup to
consider.

### Preparing for initial deployment

#### Install memcached

PWGuard uses [Memcached][] to store its session information. (I may expand its
use of Memcached, as well.) You need to ensure that the _memcached_ daemon
is running on the machine where you intend to run PWGuard.

* On Debian-based Linux systems, use `apt-get install memcached`
* On RedHat/Fedora systems, use `yum install memcached`
* On FreeBSD, use `portmaster databases/memcached`

See <https://code.google.com/p/memcached/wiki/NewInstallFromPackage> for
details.

#### Install a Java JDK

You'll need a Java runtime. The JDK is preferable to the JRE, in case you
need to debug things. Any 1.6 or better JDK will work, though I recommend
1.7 or 1.8. Once it's installed, set environment variable `JAVA_HOME`
appropriately.

#### Decide where you want your database

First, decide where you want your SQLite database to be stored. Then, make
a copy of `conf/production.conf` and override the database configuration.
The default configuration is in `conf/database.conf` and looks like this:

    db {
      default {
        url: "jdbc:sqlite:pwg.db"
        driver: "org.sqlite.JDBC"
        username: ""
        password: ""
      }
    }

To override the location, simply redefine `db.default.url` in your copy
of `production.conf`. For instance:


`my-production.conf`

    ...

    db {
      default {
        url: "jdbc:sqlite:/encryptedvol/pwguard.db"

    ...

**WARNINGS**

* PWGuard _only_ supports SQLite. The initial DDL that creates the
  database is SQLite-specific. Do _not_ attempt to use another database driver.
* The database name _must_ be `default`. If you change the name (e.g.,
  if you use `db.pwguard.url`), PWGuard will _not_ use your database
  configuration.

#### Create the distribution

Get a copy of this source code (e.g., by cloning the Git repo). Then, within
the resulting `pwguard` directory, run the following command:

    ./activator dist

That command will download the various third-party open source packages, compile
the PWGuard code, and create a distribution tarball in the `target/universal`
directory. Currently, that tarball is `pwguard-1.0-SNAPSHOT.tgz`.

#### Unpack the distribution

Copy the tarball to the appropriate location. Then, unpack it. You'll get a
`pwguard-1.0-SNAPSHOT` directory. Change your working directory to that
directory.

#### Install your configuration file

Copy your custom configuration file somewhere. Let's assume it's in
`/home/pwguard/conf/production.conf`, though you can put it anywhere.

I recommend installing your configuration file somewhere _outside_ the unpacked
distribution, to make it easier to upgrade the application.

#### Running the application

You can run the application manually, from the command line, using the
`bin/pwguard` script in the unpacked directory. If you do that, you'll
probably want to use `screen` or `tmux` to ensure that the process doesn't
die when you log off.

Ensure that `JAVA_HOME` is set appropriately and that the path to the
JDK's `bin` directory is in your `PATH`.

You'll need to pass a few arguments to the command:

    bin/pwguard -DLOG_DIR="/home/pwguard/current/logs" \
                -Dconfig.file=/home/pwguard/current/conf/production.conf \
                -DapplyEvolutions.default=true

* `-DLOG_DIR` specifies the location of the log files.
* `-Dconfig.file` specifies the path to your configuration file.
* `-DapplyEvolutions.default=true` instructs Play to apply any database
  migrations automatically.

Again, I recommend location the logs _outside_ the unpacked directory.

##### Using `supervisord`

You can also run the application under the auspices of a service such as
[Supervisor](http://supervisord.org/). The `supervisord` service must be
installed and running, and you'll have to create a configuration file for
PWGuard. You'll find a sample configuration file in `conf/supervisord.conf`.
Edit that file and install it as `pwguard.conf` (usually in
`/etc/supervisor/conf.d`); then, restart the `supervisord` service.

#### Configuring your web server

By default, PWGuard _insists_ that it be running behind an SSL-enabled
web server. It checks the HTTP `X-Forwarded-Proto` header and refuses to
do anything unless it detects that the front-end connection came in via
SSL.

You can disable this check in your configuration file:

    ensureSSL: false

**DO NOT DO THAT IN PRODUCTION.** If you do, your passwords (stored and
login) are sent **in the clear**.

If `ensureSSL` is `true` (which it is, by default, in production), if you
attempt to connect to PWGuard directly (on port 9000) or through a
non-SSL gateway, `PWGuard` will simply display

    Can't find X-Forwarded-Proto header

in the browser and refuse to do anything.

To fix this problem, you need to run PWGuard behind an SSL-enabled gateway.

I use [Nginx][], and I've included a sample `nginx.conf` file in the repo's
`conf` directory. That file is just a starting point. You may have to play with
things to get them to work in your environment.

**NOTE**: PWGuard is configured, via its Play `routes` file, to assume that
it is "mounted" on `/pwguard` in the main web server. That is, it assumes
that, for instance, a connection to `http://www.example.com/pwguard` will be
sent back to Play.

If you mount the application on a different URL, you _must_ change the
PWGuard `routes` file. Otherwise, it'll serve its assets with the wrong
path.

If you prefer to use Apache, you're on your own (though I'll gladly add
Apache instructions here, if someone works through the details and sends me
the information).

### The first login

When you point your browser at PWGuard for the first time, you'll be
presented with a login screen. Out of the box, PWGuard has a single
pre-configured user:

* **Login**: `admin@example.com`
* **Password**: `admin`

Log in as that user. Then:

* On the "Edit Users" screen, create a new user for yourself.
* Be sure to give the new user Admin privileges.
* Either delete the `admin@example.com` user or mark it inactive.

### Upgrading

Upgrading PWGuard is easy enough.

* Use `git pull` to pull down the latest code.
* Ensure that [Bower][http://bower.io/] is installed. (While most
  front-end dependencies are resolved through WebJars, some are only
  available via Bower.)
* Type `bower install`.
* Run `./activator clean dist` to rebuild the distribution.
* Copy the resulting tarball to the production directory.
* Remove the existing unpacked code (or move it out of the way).
* Unpack the new code.
* Apply any configuration changes. (See the CHANGELOG.)
* Restart the application. If you're using `supervisord`, it should be
  sufficient to kill the `java` process; `supervisord` should automatically
  restart it. Otherwise, you'll have to do the work manually.

## Building from source

See **Upgrading**, above.

## Disclaimers

### Indemnification

I use this application myself, to store my own web (and other) passwords.
Thus, I have an incentive to ensure that it works and is relatively secure.
_However_, if you use this application and your passwords are compromised
as a result, _I will not be held liable_. If you use this application, you
automatically agree to indemnify and hold harmless me, and my company, from and
against any damage, injury, loss, claim, or liability incurred as a result of
using this application, even if such damages arise from bugs in the code.

I have done my best to ensure that the application is safe and secure, but
you use it at your own risk.

## License

PWGuard is open source and is released under a New BSD license. PWGuard also
uses many excellent open source packages, most of which are listed below.

* The [Play Framework](http://www.playframework.com/)
* The [Scala][] programming language
* [Akka](http://akka.io/)
* [Angular.js](http://angularjs.org)
* [Angular File Upload](https://github.com/nervgh/angular-file-upload)
* [AngularStrap](http://mgcrea.github.io/angular-strap/)
* Portions of [Apache Commons Math](http://commons.apache.org/proper/commons-math/)
* Portions of [Apache POI](https://poi.apache.org/)
* Portions of [Apache Commons Codec](http://commons.apache.org/proper/commons-codec/)
* Twitter [Bootstrap][bootstrap]
* [CoffeeScript](http://coffeescript.org/)
* [excanvas](http://excanvas.sourceforge.net/)
* [Font Awesome](http://fontawesome.io/)
* Portions of [Google Guava](https://code.google.com/p/guava-libraries/)
* [Grizzled Scala](http://software.clapper.org/grizzled-scala/)
* [html5shiv](https://github.com/aFarkas/html5shiv)
* [JBcrypt][]
* [JodaTime](http://www.joda.org/)
* [jQuery](https://jquery.org/)
* [Log4Javascript](http://log4javascript.org/)
* [Memcached][]
* [Modernizr](http://modernizr.com/)
* [scala-csv](https://github.com/tototoshi/scala-csv)
* [SQLite](http://sqlite.org/)
* [UADetector](http://uadetector.sourceforge.net/)
* [Underscore.js](http://documentcloud.github.io/underscore/)
* [WebJars](http://www.webjars.org/)

[Play Framework]: http://playframework.org/
[AngularJS]: http://angularjs.org/
[SQLite]: http://www.sqlite.org/
[Nginx]: http://nginx.org/
[JBCrypt]: http://www.mindrot.org/projects/jBCrypt/
[Scala]: http://www.scala-lang.org/
[bootstrap]: http://getbootstrap.com/
[Memcached]: http://www.memcached.org/
