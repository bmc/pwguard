# PWGuard - A password vault

## Overview

PWGuard implements a simple web-based password vault, using the
[Play Framework][] and [AngularJS][]. PWGuard allows for
a relatively secure deployment, by providing the following capabilities:

* By default, it uses a [SQLite][] database. Since SQLite confines its data to
  a single file, you can easily locate that file on an encrypted volume.
* By putting an SSL-aware web server, such as [Nginx][], in front of the
  running PWGuard server, you can encrypt the data in-transit.
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
* [Memcached](http://www.memcached.org/)
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
