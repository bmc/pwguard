/*
 Custom AngularJS services.

 This code is ES6-based and must be transpiled with traceur. JSHint doesn't
 grok ES6, either, so it must be disabled for this file.

 WARNING: Do NOT include this file in the HTML. sbt-traceur automatically
 mixes it into the main Javascript file.
 */

/* jshint ignore:start */

var pwgServices = angular.module('pwguard-services', []);

pwgServices.value('angularTemplateURL', window.angularTemplateURL);

// ----------------------------------------------------------------------------
// Logging service. Basically, this service simply hides the initialization
// of a log4javascript-compatible logging service, providing a simple
// logger() function to retrieve a new logger.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgLogging', function() {
  var log      = log4javascript.getLogger();
  var appender = new log4javascript.BrowserConsoleAppender();

  log4javascript.setShowStackTraces(true);

  // We could allow the console appender to use the default NullLayout,
  // which allows for the logging of objects without converting them to
  // strings. But other info (e.g., component and timestamp) are lost that
  // way. To log objects, dump them as JSON.

  appender.setLayout(
    new log4javascript.PatternLayout("%d{HH:mm:ss.SSS} (%-5p) %c: %m")
  );

  log.addAppender(appender);

  function mapLevel(stringLevel) {

    var level = null;
    switch (stringLevel) {
      case 'trace':
        level = log4javascript.Level.TRACE;
        break;
      case 'debug':
        level = log4javascript.Level.DEBUG;
        break;
      case 'warn':
        level = log4javascript.Level.WARN;
        break;
      case 'error':
        level = log4javascript.Level.ERROR;
        break;
      case 'fatal':
        level = log4javascript.Level.FATAL;
        break;
      case 'info':
        // fall through is intentional
      default:
        level = log4javascript.Level.INFO;
        break;
    }

    return level;
  }

  var loggingLevel = mapLevel(window.browserLogLevel)
  appender.setThreshold(loggingLevel)

  return {
    logger: function(name, level = 'debug') {
      var logger = log4javascript.getLogger(name);
      logger.addAppender(appender);
      logger.setLevel(mapLevel(level));
      return logger;
    }
  }

});

// ----------------------------------------------------------------------------
// Error service.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgError', function() {
  return {
    showStackTrace: function(prefix = "") {
      if (prefix) console.log(prefix);
      console.log(new Error().stack);
    }
  }
});

// ----------------------------------------------------------------------------
// Front-end service for AJAX calls. Handles errors in a consistent way, and
// fires up a spinner.
//
// The service provides the following functions. All functions return an
// Angular $q promise. On success, the promise is resolved with the response
// data, the status, and the headers. On failure, the promise is rejected with
// the error data, the status, and the headers. That way, callers can decide
// how and whether they want to handle success and failure.
//
// post(url, data)
//   Post (JavaScript) data to the specified URL.
//
// postWithProgress(url, data, showSpinner = false)
//   Post data to the specified URL, posting progress notifications to the
//   returned promise. By default, the spinner is not used during this request,
//   because the caller is (probably) displaying a progress bar. If the spinner
//   is desired, specify a third argument of true.
//
// get(url)
//   Issue an HTTP GET to the specified URL.
//
// delete(url, data)
//   Issue an HTTP DELETE to the specified URL, passing the specified
//   (JavaScript) data.
//
// Example:
//
// var promise = pwgAjax.get(url);
// promise.then(function(response) {
//   var data = response.data
//   var status = response.status
//   var headers = response.headers
//   ...
// });
// ----------------------------------------------------------------------------

pwgServices.factory('pwgAjax', ng(function($injector) {
  var $http      = $injector.get('$http');
  var $q         = $injector.get('$q');
  var pwgSpinner = $injector.get('pwgSpinner');
  var pwgFlash   = $injector.get('pwgFlash');

  var callOn401 = null;

  function handleSuccess(deferred, data, status, headers) {
    // Angular doesn't seem to handle 401 responses properly, so we're
    // mimicking them with JSON.
    //
    // NOTE: This happens when an HTTP interceptor is injected. Without
    // the interceptor, Angular behaves correctly.

    if (data.error) {
      console.log(response);
      if (data.error.message)
        pwgFlash.error(response.error.message);

      deferred.reject({
        data:    data,
        status:  status,
        headers: headers
      });
    }

    else {
      deferred.resolve({
        data:    data,
        status:  status,
        headers: headers
      });
    }
  }

  function handleFailure(deferred, data, status, headers) {
    var message = null;
console.log(`*** `, data);
    if (data && data.error && data.error.message)
      message = data.error.message;
    else
      message = "Server error. We're looking into it.";

    if (status === 401) {
      data = {
        error: {
          status:  401,
          message: "Login required"
        }
      }

      if (callOn401) callOn401();
    }
    else {
      pwgFlash.error(`(${status}) ${message}`, status);
      deferred.reject({
        data:    data,
        status:  status,
        headers: headers
      });
    }
  }

  function http(config) {
    var deferred = $q.defer();
    var promise  = deferred.promise;

    function failed(data, status, headers, config) {
      pwgSpinner.stop();
      handleFailure(deferred, data, status, headers);
    }

    function succeeded(data, status, headers, config) {
      pwgSpinner.stop();
      handleSuccess(deferred, data, status, headers);
    }

    pwgSpinner.start();

    $http(config).success(succeeded).error(failed);
    return promise;
  }

  function doPost(url, data) {
    var params = {
      method: 'POST',
      url:    url,
      data:   data
    }

    if (! url)
      throw new Error("No URL for pwgAjax.post()");

    return http(params);
  }

  function doPostFile(url, file, showSpinner = false) {
    // $http doesn't support progress callback, so we're dropping down
    // to the browser interface. See
    // http://www.sagarganatra.com/2011/04/file-upload-and-progress-events-with.html
    let deferred = $q.defer();
    let promise  = deferred.promise;

    if (showSpinner)
      pwgSpinner.start();

    let req = new window.XMLHttpRequest();

    // Progress handling.

    req.upload.addEventListener("loadstart", (e) => {}, false);
    req.upload.addEventListener("progress", function(e) {
      if (e.lengthComputable) {
        let percentComplete = Math.round((e.loaded * 100) / e.total);
        deferred.notify(percentComplete);
      }
      else {
        deferred.notify(0);
      }
    }, false);
    req.upload.addEventListener("loadend", function(e) {
      deferred.notify(100);
    }, false);

    // Error handling.

    function handleUploadError(e) {
      console.log("error", e);
    }

    req.upload.addEventListener("error", handleUploadError, false);
    req.upload.addEventListener("abort", handleUploadError, false);

    // Completion handling.

    req.upload.addEventListener("load", function(e) {
      // Have to handle the AngularJS JSONP protection manually, since
      // we've bypassed Angular completely.
      //
    }, false);

    function failed(data, status) {
      if (showSpinner)
        pwgSpinner.stop();

      handleFailure(deferred, data, status, {});
    }

    function succeeded(data, status) {
      if (showSpinner)
        pwgSpinner.stop();

      handleSuccess(deferred, data, status, {});
    }

    req.onreadystatechange = function() {
      // 4 === complete. See
      // https://developer.mozilla.org/en-US/docs/AJAX/Getting_Started
      if (req.readyState != 4) return;

      function processResponse(req) {
        if (req.responseText)
          return JSON.parse(req.responseText.replace(/^\)]}',\n/, ""));
        else
          return null;
      }

      try {
        if (req.status === 200)
          succeeded(processResponse(req), req.status);
        else
          failed(processResponse(req), req.status);
      }
      catch(e) {
        failed(e.message, 500);
      }
    }

    req.open("POST", url, true);
    req.setRequestHeader("Content-type", file.type);
    req.setRequestHeader("X-File-Name", file.name);

    // Response type MUST be "text", not "json", because it could contain
    // AngularJS-compatible JSONP vulnerability protection, which means it
    // cannot be parsed as JSON without some work.
    req.responseType = "text";
    req.send(file);

    return promise;
  }

  function doGet(url) {
    var params = {
      method: 'GET',
      url:    url
    }

    if (! url)
      throw new Error("No URL for pwgAjax.get()");

    return http(params);
  }

  function doDelete(url, data) {
    var params = {
      method:  'DELETE',
      url:     url,
      data:    data,
      headers: null
    }

    if (data) {
      params.headers = {
        'Content-Type': 'application/json'
      }
    }

    if (! url)
      throw new Error("No URL for pwgAjax.delete()");

    return http(params);
  }

  return {
    post:       doPost,
    postFile:   doPostFile,
    get:        doGet,
    delete:     doDelete,
    on401: function(callback) {
      callOn401 = callback;
    }
  }
}));

// ----------------------------------------------------------------------------
// A timeout service with arguments in a more sane order.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgTimeout', ng(function($timeout) {
  return {
    cancel:  function(promise) { return $timeout.cancel(promise); },
    timeout: function(timeout, callback) { return $timeout(callback, timeout); }
  }
}));

// ----------------------------------------------------------------------------
// Get info about the currently logged-in user
// ----------------------------------------------------------------------------

pwgServices.factory('pwgUser', ng(function($injector) {

  var pwgLogging  = $injector.get('pwgLogging');
  var pwgAjax     = $injector.get('pwgAjax');
  var currentUser = null;
  var $q          = $injector.get('$q');

  function isLoggedIn() {
    return currentUser !== null;
  }

  return {
    checkUser: function() {
      var deferred = $q.defer();
      var url = routes.controllers.SessionController.getLoggedInUser().url;

      pwgAjax.post(url, {}).then(
        function(response) { // on success
          var data = response.data;
          if (data.loggedIn)
            currentUser = data.user;
          else
            currentUser = null;

          if (deferred)
            deferred.resolve(data);

          deferred = null;
        },

        function(response) { // on failure
          if (deferred)
            deferred.reject(response.data);

          deferred = null;
        }
      );

      return deferred.promise;
    },

    currentUser: function() {
      return currentUser;
    },

    setLoggedInUser: function(user) {
      currentUser = user;
    },

    isLoggedIn: isLoggedIn,

    userIsAdmin: function() {
      return isLoggedIn() && currentUser.admin;
    }
  }

}));

// ----------------------------------------------------------------------------
// Check a form. If it's been edited, prompt the user for cancellation.
// If the user confirms, or if the form is pristine, route to the named
// location.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgFormHelper', ng(function($injector) {

  var pwgRoutes = $injector.get('pwgRoutes');
  var pwgModal  = $injector.get('pwgModal');

  return {
    validateCancellation: function(form, routeOnConfirm) {
      let doCancel = function() {
        pwgRoutes.redirectToNamedRoute(routeOnConfirm);
      }

      if (form.$dirty) {
        pwgModal.confirm("The form has unsaved changes. Really cancel?",
                         "Confirm cancellation.").then(doCancel);
      }
      else {
        doCancel();
      }
    }
  }
}));

// ----------------------------------------------------------------------------
// Form help service
// ----------------------------------------------------------------------------

pwgServices.factory('pwgForm', ng(function() {
  return {
    setValid: (form, flag) => {
      if (! form) return;

      form.$valid   = flag;  // hack
      form.$invalid = !flag; // hack
    },

    setDirty: (form, flag) => {
      if (! form) return;

      if (flag)
        form.$setDirty();
      else
        form.$setPristine();
    }
  }
}));

// ----------------------------------------------------------------------------
// Service to manage saving last search term
// ----------------------------------------------------------------------------

pwgServices.factory('pwgSearchTerm', ng(function($injector) {
  var $cookieStore = $injector.get('$cookieStore');
  var SAVED_TERM_COOKIE = "lastSearch";

  return {
    saveSearchTerm: (term) => { $cookieStore.put(SAVED_TERM_COOKIE, term); },
    clearSavedTerm: ()     => { $cookieStore.remove(SAVED_TERM_COOKIE); },
    getSavedTerm:   ()     => { return $cookieStore.get(SAVED_TERM_COOKIE); }
  }
}));

// ----------------------------------------------------------------------------
// Route-related services
// ----------------------------------------------------------------------------

pwgServices.factory('pwgRoutes', ng(function($injector) {

  var pwgLogging              = $injector.get('pwgLogging');
  var pwgError                = $injector.get('pwgError');
  var pwgFlash                = $injector.get('pwgFlash');
  var $location               = $injector.get('$location');
  var $route                  = $injector.get('$route');
  var DEFAULT_ROUTE_NAME      = null;
  var POST_LOGIN_ROUTES       = [];
  var PRE_LOGIN_ROUTES        = [];
  var ADMIN_ONLY_ROUTES       = [];
  var REVERSE_ROUTES          = {};
  var ROUTES_BY_NAME          = {};

  var log = pwgLogging.logger("pwgRoutes");

  for (var pattern in $route.routes) {
    let route = $route.routes[pattern];
    if (!route)
      continue;

    ROUTES_BY_NAME[route.name] = route;

    if (route.isDefault) { // This is the default route
      DEFAULT_ROUTE_NAME = route.defaultName;
    }
    else if (route.name) {
      // AngularJS adds routes. If the name field is present, it's one
      // we added.
      REVERSE_ROUTES[route.name] = pattern;

      if (route.postLogin) POST_LOGIN_ROUTES.push(route.name);
      if (route.preLogin) PRE_LOGIN_ROUTES.push(route.name);
      if (route.admin) ADMIN_ONLY_ROUTES.push(route.name);
    }
  }

  var URL_RE = /^.*#(.*)$/;

  var routeForName = (name) => {
    return ROUTES_FOR_NAME[name];
  }

  var isPostLoginRoute = (name) => {
    return (POST_LOGIN_ROUTES.indexOf(name) >= 0);
  }

  var isPreLoginRoute = (name) => {
    return (PRE_LOGIN_ROUTES.indexOf(name) >= 0);
  }

  var substituteParams = (url, params) => {
    let keys = _.keys(params)

    // Replace any :param, :param? and :param* keys in the URL pattern with
    // parameters from the supplied object.
    for (let i = 0; i < keys.length; i++) {
      var k = keys[i];
      var re = new RegExp(":" + k + "[?*]?")
      url = url.replace(re, params[k]);
    }

    // If there are any remaining ":" parameters, puke.
    if (/:[a-zA-Z0-9_]+/.test(url))
      throw new Error(`Substituted URL ${url} still has parameter patterns.`);

    return url;
  }

  var pathForRouteName = (name, params = {}) => {
    let urlPattern = REVERSE_ROUTES[name];
    if (urlPattern) {
      return substituteParams(urlPattern, params);
    }
    else {
      throw new Error(`(BUG) No URL for route ${name}`)
    }
  }

  var hrefForRouteName = (name, params = {}) => {
    return "#" + pathForRouteName(name, params);
  }

  var redirectToNamedRoute = (name, params = {}) => {
    let url = pathForRouteName(name, params);
    log.debug(`Redirecting to ${url}`)
    $location.path(url);
  }

  function routeNameForURL(url) {
    var route = routeForURL(url);
    if (route)
      return route.name;
    else
      return undefined;
  }

  function routeForURL(url) {
    if (!url) url = "";
    let m = URL_RE.exec(url);
    let strippedURL;
    if (m)
      strippedURL = m[1];
    else
      strippedURL = url;

    let result = null;
    for (var pattern in $route.routes) {
      let r = $route.routes[pattern];
      if (! r.name)
        continue;
      if (! r.regexp)
        continue;
      if (r.regexp.test(strippedURL)) {
        result = r;
        break;
      }
    }

    return result;
  }

  var DEFAULT_ROUTE_PATH = pathForRouteName(DEFAULT_ROUTE_NAME);
  var DEFAULT_ROUTE_HREF = hrefForRouteName(DEFAULT_ROUTE_NAME);

  return {
    isAdminOnlyRoute: (name) => { return ADMIN_ONLY_ROUTES.indexOf(name) >= 0; },

    isPostLoginRoute: (name) => { return isPostLoginRoute(name); },

    isPreLoginRoute: (name) => { return isPreLoginRoute(name); },

    hrefForRouteName: (name, params = {}) => {
      return hrefForRouteName(name, params);
    },

    pathForRouteName: (name, params = {}) => {
      return pathForRouteName(name, params);
    },

    routeForRouteName: (name) => { return routeForName(name); },

    // These are functions to ensure no one can modify the values.
    defaultRouteName: () => { return DEFAULT_ROUTE_NAME; },

    defaultRoutePath: () => { return pathForRouteName(DEFAULT_ROUTE_NAME); },

    defaultRouteHref: () => { return hrefForRouteName(DEFAULT_ROUTE_NAME); },

    redirectToDefaultRoute: () => { redirectToNamedRoute(DEFAULT_ROUTE_NAME); },

    routeIsActive: (name) => {
      let path = pathForRouteName(name);
      return (path && $location.path().endsWith(path));
    },

    redirectToNamedRoute: (name, params = {}) => {
      redirectToNamedRoute(name, params);
    },

    routeNameForURL: (url) => { return routeNameForURL(url); },

    routeForURL: (url) => { return routeForURL(url); },

    currentRouteName: () => { return routeNameForURL($location.path()); }
  }
}));

pwgServices.factory('pwgCheckRoute', ng(function($injector) {
  var pwgRoutes  = $injector.get('pwgRoutes');
  var pwgLogging = $injector.get('pwgLogging');

  var log = pwgLogging.logger('pwgCheckRoute');
  return function(routeName, currentUser) {
    log.debug(`checking ${routeName}. Current user: ${JSON.stringify(currentUser)}`);

    if (currentUser) { // logged in
      if (pwgRoutes.isPostLoginRoute(routeName)) {
        log.debug(`${routeName} is a post-login route. Staying here.`);
        // Okay to stay here
      }
      else {
        // Can't stay here. Off to the default page.
        log.debug(`${routeName} is not a post-login route. Redirecting.`);
        pwgRoutes.redirectToDefaultRoute();
      }
    }

    else { // not logged in
      if (pwgRoutes.isPreLoginRoute(routeName)) {
        log.debug(`${routeName} is a pre-login route. Staying here.`);
        // Okay to stay here
      }
      else {
        // Can't stay here; it's not a pre-login route. Off to the login
        // page.
        log.debug(`${routeName} is not a pre-login route. Redirecting.`);
        pwgRoutes.redirectToNamedRoute('login');
      }
    }
  }
}));

/* jshint ignore:end */
