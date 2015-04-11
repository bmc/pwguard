# PWGuard Change Log

Version 1.0.3 (...)

* AJAX autoload of keywords on "new entry" screen no longer displays loading
  spinner modal.
* All functions in `pwgAjax` service now permit disabling spinner, if caller
  desires.
* Suppress annoying dotted line that appears (in Firefox) when a navbar link
  is clicked.

Version 1.0.2 (Mar 21, 2015)

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
* XML import/export format now supported, as a more reliable backup mechanism.
* From the search results screen, you can now create a copy of an entry.
* The "loading" spinner is now a full-page, blocking modal.
* Updated Twitter Bootstrap to 3.3.2-2.
* Upgraded to AngularJS 1.3.
* Removed dependence on AngularStrap, replacing it with local implementations
  of necessary Bootstrap controls.
* Added tooltips to various buttons.
* Clicking on unexpanded URL in search results now opens the same new tab
  as other URLs.
* `pwgAjax` service functions now return a promise, for better composition.
* Changed import file upload to use AJAX file upload, with progress data,
  for a better user experience. Added an upload progress bar.

Version 1.0.1 (Mar 4, 2015)

* Disabled use of JsHint, because Traceur is good enough.
* Rebuilt `traceur` SBT task as a custom task, primarily to enable easy use
  of `angular-injector`.
* Created helper SBT functions.

Version 1.0.0 (Mar 2, 2015)

* Initial version.

