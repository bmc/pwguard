# PWGuard - A password vault

PWGuard implements a simple web-based password vault, using the
[Play! Framework][] and [AngularJS][]. PWGuard allows for
a relatively secure deployment, by providing the following capabilities:

* By default, it uses a [SQLite][] database. Since SQLite confines its data to
  a single file, you can easily locate that file on an encrypted volume.
* By putting an SSL-aware web server, such as [Nginx][], in front of the
  running PWGuard server, you can encrypt the data in-transit.
* User login passwords are one-way encrypted via [BCrypt][JBCrypt].
* Stored passwords are encrypted with a user-specific (symmetric) key,
  which is kept in the database.

PWGuard is open source and is released under a New BSD license. PWGuard also
uses many excellent open source packages, most of which are listed below.

* The [Play! Framework](http://www.playframework.com/)
* The [Scala][] programming language
* [Akka](http://akka.io/)
* [Angular.js](http://angularjs.org)
* Portions of [Apache Commons Math](http://commons.apache.org/proper/commons-math/)
* [Font Awesome](http://fontawesome.io/)
* Portions of [Google Guava](https://code.google.com/p/guava-libraries/)
* [Twitter Bootstrap](http://getbootstrap.com/)
* [Grizzled Scala](http://software.clapper.org/grizzled-scala/)
* [JBcrypt][]
* [JodaTime](http://www.joda.org/)
* [jQuery](https://jquery.org/)
* [Log4Javascript](http://log4javascript.org/)
* [Modernizr](http://modernizr.com/)
* [Moment.js](http://momentjs.com/)
* [SQLite](http://sqlite.org/)
* [UADetector](http://uadetector.sourceforge.net/)
* [Underscore.js](http://documentcloud.github.io/underscore/)
* [WebJars](http://www.webjars.org/)

[Play! Framework]: http://playframework.org/
[AngularJS]: http://angularjs.org/
[SQLite]: http://www.sqlite.org/
[Nginx]: http://nginx.org/
[JBCrypt]: http://www.mindrot.org/projects/jBCrypt/
[Scala]: http://www.scala-lang.org/
