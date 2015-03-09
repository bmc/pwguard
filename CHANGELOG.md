# PWGuard Change Log

Version 1.0.2 (Mar ..., 2014)

* Added ability to save session for 10 days, on login. If not specified,
  assumes 15-minute default.
* The search term (on the search page) is now added to the URL query string,
  allowing it to be bookmarked. When the term is restored and reissued
  (e.g., upon reload), the query string is checked before the saved term
  in the session cookie.
* PWGuard now supports explicit "security questions" fields that behave more
  or less like custom fields, with a couple minor differences:
  - Because PWGuard _knows_ they are security questions, the user doesn't have
    to number them explicitly.
  - They can be handled and displayed slightly differently (and more
    appropriately) than the more free-form custom fields.
* The "loading" spinner is now a full-page, blocking modal.

Version 1.0.1 (Mar 4, 2014)

* Disabled use of JsHint, because Traceur is good enough.
* Rebuilt `traceur` SBT task as a custom task, primarily to enable easy use
  of `angular-injector`.
* Created helper SBT functions.

Version 1.0.0 (Mar 2, 2015)

* Initial version.

