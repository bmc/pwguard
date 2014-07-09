# PWGuard - A password vault

PWGuard implements a simple web-based password vault, using the
[Play! Framework][] and [AngularJS][]. PWGuard allows for
a relatively secure deployment, by providing the following capabilities:

* By default, it uses a [SQLite][] database. Since SQLite confines its data to
  a single file, you can easily locate that file on an encrypted volume.
* By putting an SSL-aware web server, such as [Nginx], in front of the
  running PWGuard server, you can encrypt the data in-transit.
* User login passwords are one-way encrypted via [BCrypt][JBCrypt].
* Stored passwords are encrypted with a user-specific (symmetric) key,
  which is kept in the database.

PWGuard is open source and is released under a New BSD license. PWGuard also
uses many excellent open source packages, most of which are listed below.

* [Akka](http://akka.io/)
* [Angular.js](http://angularjs.org)
* [angular-route-segment](http://angular-route-segment.com/)
* [angular-chosen](https://github.com/localytics/angular-chosen)
* [angular-tablesort](https://mattiash.github.io/angular-tablesort/)
* Portions of [AngularUI](http://angular-ui.github.io/)
* [Animate.css](https://daneden.github.io/animate.css/)
* Portions of [Apache Commons Math](http://commons.apache.org/proper/commons-math/)
* [Chosen](https://harvesthq.github.io/chosen/)
* [Clippy](https://github.com/mojombo/clippy)
* Portions of [MacGyver](http://starttheshift.github.io/MacGyver/)
* [Font Awesome](http://fontawesome.io/)
* Portions of [Google Guava](https://code.google.com/p/guava-libraries/)
* [Gridism](http://cobyism.com/gridism/)
* [Grizzled Scala](http://software.clapper.org/grizzled-scala/)
* [JBcrypt][]
* [JodaTime](http://www.joda.org/)
* [jQuery](https://jquery.org/)
* Portions of [JqueryUI](http://jqueryui.com/)
* [Log4Javascript](http://log4javascript.org/)
* [Modernizr](http://modernizr.com/)
* [Moment.js](http://momentjs.com/)
* The [Play! Framework](http://www.playframework.com/)
* The [Scala][] programming language
* [SQLite](http://sqlite.org/)
* [UADetector](http://uadetector.sourceforge.net/)
* [Underscore.js](http://documentcloud.github.io/underscore/)

[Play! Framework]: http://playframework.org/
[AngularJS]: http://angularjs.org/
[SQLite]: http://www.sqlite.org/
[Nginx]: http://nginx.org/
[JBCrypt]: http://www.mindrot.org/projects/jBCrypt/
[Scala]: http://www.scala-lang.org/
