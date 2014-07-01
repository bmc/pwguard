# PWGuard - A password vault

PWGuard implements a simple web-based password vault, using the
[Play! Framework][] and [AngularJS][]. PWGuard allows for
a relatively secure deployment, by providing the following capabilities:

* By default, it uses a [SQLite][] database. Since SQLite confines its data to
  a single file, you can easily locate that file on an encrypted volume.
* By putting an SSL-aware web server, such as [Nginx], in front of the
  running PWGuard server, you can encrypt the data in-transit.
* User login passwords are one-way encrypted via [BCrypt][].
* Stored passwords are encrypted with a user-specific (symmetric) key,
  which is kept in the database.

[Play! Framework]: http://playframework.org/
[AngularJS]: http://angularjs.org/
[SQLite]: http://www.sqlite.org/
[Nginx]: http://nginx.org/
[BCrypt]: http://www.mindrot.org/projects/jBCrypt/
