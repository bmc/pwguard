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
// Simple flash service. Use in conjunction with the pwg-flash directive.
//
// This service sets or clears the following variables in the root scope:
//
// flash.message.info    - info alert message
// flash.message.error   - error messages
// flash.message.warning - warning alert messages
//
// The service provides the following functions. These functions are also
// available on the $rootScope.flash object, for use in HTML.
//
// init()             - CALL THIS FIRST at application startup.
// warn(msg)          - issue a warning message
// info(msg)          - issue an info message
// error(msg)         - issue an error message
// message(type, msg) - issue a message of the specified type. The types can
//                      be 'warn', 'info', 'error', 'all'
// clear(type)        - clear message(s) of the specified type. The types can
//                      be 'warn', 'info', 'error', 'all'
// clearInfo()        - convenience
// clearWarning()     - convenience
// clearError()       - convenience
// clearAll()         - convenience
// ----------------------------------------------------------------------------

pwgServices.factory('pwgFlash', ng(function($timeout) {

  var alerts = {};

  function clearAlert(type) {
    let alert = alerts[type];
    if (alert)
      alert.hide();
  }

  function doAlert(content, type, timeout=0) {
     let alert = alerts[type];
    if (alert) {
      alert.show(content);
      if (timeout > 0) {
        $timeout(function() { clearAlert(type) }, timeout * 1000);
      }
    }
  }

  return {
    warn: (msg, timeout=0) => {
      clearAlert('warning');
      doAlert(msg, 'warning', timeout);
    },

    error: (msg, timeout=0) => {
      clearAlert('error');
      doAlert(msg, 'error', timeout);
    },

    info: (msg, timeout=0) => {
      clearAlert('info');
      doAlert(msg, 'info', timeout);
    },

    clearError:   () => { clearAlert('error'); },
    clearWarning: () => { clearAlert('warning'); },
    clearInfo:    () => { clearAlert('info'); },

    clearAll: () => {
      for (let type in alerts) {
        if (type && alerts.hasOwnProperty(type)) {
          clearAlert(type)
        }
      }
    },

    // This function is INTERNAL ONLY. Do not call it.
    _registerFlashBox: (type, show, hide) => {
      alerts[type] = {show: show, hide: hide}
    }
  }

}));

// ----------------------------------------------------------------------------
// Front-end service for AJAX calls. Handles errors in a consistent way, and
// fires up a spinner.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgAjax', ng(function($injector) {
  var $http      = $injector.get('$http');
  var pwgSpinner = $injector.get('pwgSpinner');
  var pwgFlash   = $injector.get('pwgFlash');
  var pwgError   = $injector.get('pwgError');

  var callOn401 = null;

  function handleFailure(data, status, onFailure) {
    var message = null;
    if (data.error && data.error.message)
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
      if (onFailure)
        onFailure(data);
    }
  }

  function handleSuccess(response, status, onSuccess, onFailure) {

    // Angular doesn't seem to handle 401 responses properly, so we're
    // mimicking them with JSON.
    //
    // NOTE: This happens when an HTTP interceptor is injected. Without
    // the interceptor, Angular behaves correctly.

    if (response.error) {
      console.log(response);
      if (response.error.message)
        pwgFlash.error(response.error.message);
      if (onFailure)
        onFailure(response);
    }
    else {
      if (onSuccess)
        onSuccess(response);
    }
  }

  function http(config, onSuccess, onFailure) {
    function failed(data, status, headers, config) {
      pwgSpinner.stop();
      handleFailure(data, status, onFailure);
    }

    function succeeded(data, status, headers, config) {
      pwgSpinner.stop();
      handleSuccess(data, status, onSuccess, onFailure);
    }

    pwgSpinner.start();
    $http(config).success(succeeded).error(failed);
  }

  return {

    post: function(url, data, onSuccess, onFailure) {
      var params = {
        method: 'POST',
        url:    url,
        data:   data
      }

      if (url)
        http(params, onSuccess, onFailure);
      else
        pwgError.showStackTrace("No URL for pwgAjax.post()");
    },

    get: function(url, onSuccess, onFailure = null) {
      var params = {
        method: 'GET',
        url:    url
      }

      if (url)
        http(params, onSuccess, onFailure);
      else
        pwgError.showStackTrace("No URL for pwgAjax.get()");
    },

    delete: function(url, data, onSuccess, onFailure = null) {
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

      http(params, onSuccess, onFailure)
    },

    on401: function(callback) {
      callOn401 = callback;
    }
  }
}));

// ----------------------------------------------------------------------------
// Simple spinner service. Assumes the existence of an element that's monitoring
// the root scope's "showSpinner" variable.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgSpinner', ng(function($rootScope) {
  // This scope is registered pwgSpinner directives scope.
  var $scope = null;
  function setSpinner(onOff) {
    if ($scope)
      $scope.showSpinner = onOff;
  }

  return {
    start: function() { setSpinner(true); },
    stop:  function() { setSpinner(false); },

    // _registerDirective() is only used by the companion pwgSpinner directive.
    // DO NOT CALL THIS FUNCTION.
    _registerDirective: function(scope) {
      $scope = scope;
      setSpinner(false);
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

      pwgAjax.post(url, {},
        function(response) { // on success
          if (response.loggedIn)
            currentUser = response.user;
          else
            currentUser = null;

          if (deferred)
            deferred.resolve(response);

          deferred = null;
        },

        function(response) { // on failure
          if (deferred)
            deferred.reject(response);

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
// Modal service. Hides underlying implementation(s).
// ----------------------------------------------------------------------------

pwgServices.factory('pwgModal', ng(function($injector) {

  var $q = $injector.get('$q');

  var mobile = window.browserIsMobile;

  // Shows an appropriate confirmation dialog, depending on whether the user
  // is mobile or not. Returns a promise (via $q) that resolves on confirmation
  // and rejects on cancel.
  //
  // Parameters:
  //   message - the confirmation message
  //   title   - optional title for the dialog, if supported
  //
  // NOTE: Only one of these can be active at one time!

  var directiveShowFunc = null;
  var deferred          = null;

  return {
    confirm: function(message, title = null) {
      deferred = $q.defer();

      if (mobile) {

        // Use standard confirmation dialog, rather than in-browser one.

        if (confirm(message))
          deferred.resolve();
        else
          deferred.reject();
      }

      else {

        // Use in-browser one.

        if (directiveShowFunc)
          directiveShowFunc(message, title);
        else
          throw new Error("No directive show function to invoke.");
      }

      return deferred.promise;
    },

    // Internal use only. DO NOT CALL. Called by the associated directive.
    //
    // Parameters:
    //   show - A function taking a message to display and an optional title
    //
    // Returns: { onOK: [function], onCancel: [function] }
    _registerModal: (show) => {
      directiveShowFunc = show;
      return {
        onOK: () => {
          if (deferred) {
            deferred.resolve();
            deferred = null;
          }
        },
        onCancel: () => {
          if (deferred) {
            deferred.reject();
            deferred = null;
          }
        }
      }
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
      var re = new RegExp(":" + k + "[?*]")
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
