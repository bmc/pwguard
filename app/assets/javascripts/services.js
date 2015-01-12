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

  // Allow the console appender to use the default NullLayout, which allows
  // for the logging of objects without converting them to strings.

  /*
   var layout   = new log4javascript.PatternLayout("%d{HH:mm:ss} (%-5p) %c %m")
   appender.setLayout(layout)
   */

  log.addAppender(appender);

  function mapLevel(stringLevel) {

    var level = null;
    if (stringLevel === 'trace')      level = log4javascript.Level.TRACE;
    else if (stringLevel === 'debug') level = log4javascript.Level.DEBUG;
    else if (stringLevel === 'info')  level = log4javascript.Level.INFO;
    else if (stringLevel === 'warn')  level = log4javascript.Level.WARN;
    else if (stringLevel === 'error') level = log4javascript.Level.ERROR;
    else if (stringLevel === 'fatal') level = log4javascript.Level.FATAL;
    else                              level = log4javascript.Level.INFO;

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

pwgServices.factory('pwgFlash', ['$alert', function($alert) {

  var infoAlert     = null
  var errorAlert    = null
  var warningAlert  = null

  function clearError() {
    if (errorAlert) {
      errorAlert.hide();
      errorAlert = null;
    }
  }

  function clearWarning() {
    if (warningAlert) {
      warningAlert.hide();
      warningAlert = null;
    }
  }

  function clearInfo() {
    if (infoAlert) {
      infoAlert.hide();
      infoAlert = null;
    }
  }

  function doAlert(content, type, timeout=0) {
    return $alert({
      title:     "",
      content:   content,
      placement: 'top-right',
      type:      type,
      show:      true,
      template:  routes.staticAsset("AngularTemplates/alert.html"),
      container: '.navbar',
      duration:  timeout
    });
  }

  return {
    warn: function(msg, timeout=0) {
      clearWarning();
      warningAlert = doAlert(msg, 'warning', timeout);
    },

    error: function(msg, timeout=0) {
      clearError();
      errorAlert = doAlert(msg, 'danger', timeout);
    },

    info: function(msg, timeout=0) {
      clearInfo();
      infoAlert = doAlert(msg, 'info', timeout);
    },

    clearError: clearError,
    clearWarning: clearWarning,
    clearInfo: clearInfo,

    clearAll: function() {
      clearError();
      clearWarning();
      clearInfo();
    }
  }

}]);

// ----------------------------------------------------------------------------
// Front-end service for AJAX calls. Handles errors in a consistent way, and
// fires up a spinner.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgAjax', ['$injector', function($injector) {
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

    pwgFlash.clearAll();
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
}])

// ----------------------------------------------------------------------------
// Simple spinner service. Assumes the existence of an element that's monitoring
// the root scope's "showSpinner" variable.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgSpinner', ['$rootScope', function($rootScope) {
  function pwgSpinner($rootScope) {
    $rootScope.showSpinner = true;
  }

  return {
    start: function() { $rootScope.showSpinner = true; },
    stop:  function() { $rootScope.showSpinner = false; }
  }
}])

// ----------------------------------------------------------------------------
// A timeout service with arguments in a more sane order.
// ----------------------------------------------------------------------------

pwgServices.factory('pwgTimeout', ['$timeout', function($timeout) {
  return {
    cancel:  function(promise) { $timeout.cancel(promise); },
    timeout: function(timeout, callback) { $timeout(callback, timeout); }
  }
}]);


// ----------------------------------------------------------------------------
// Get info about the currently logged-in user
// ----------------------------------------------------------------------------

pwgServices.factory('pwgCheckUser', ['$q', 'pwgAjax', function($q, pwgAjax) {

  var deferred = null;

  return {
    checkUser: function() {
      deferred = $q.defer();
      var url = routes.controllers.SessionController.getLoggedInUser().url;

      pwgAjax.post(url, {},
        function(response) { // on success
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
    }
  }

}]);


// ----------------------------------------------------------------------------
// Modal service. Hides underlying implementation(s).
// ----------------------------------------------------------------------------

pwgServices.factory('pwgModal', ['$injector', function($injector) {

  var $q         = $injector.get('$q');
  var $modal     = $injector.get('$modal');
  var $rootScope = $injector.get('$rootScope');

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

  return {
    confirm: function(message, title) {
      var deferred = $q.defer();

      if (mobile) {

        // Use standard confirmation dialog, rather than in-browser one.

        if (confirm(message))
          deferred.resolve();
        else
          deferred.reject();
      }

      else {

        // Use in-browser one.

        let modal = $modal({
          title:    title,
          template: routes.staticAsset("AngularTemplates/confirmModal.html"),
          backdrop: 'static',
          content:  message,
          show:     false
        });

        // Bound to values in the template.
        $rootScope.modalConfirmOK = function() {
          deferred.resolve();
          modal.hide();
        }

        $rootScope.modalConfirmCancel = function() {
          deferred.reject();
          modal.hide();
        }

        $rootScope.modalConfirmKeyPressed = function($event) {
          if ($event.keyCode === 13) // ENTER
            $scope.modalConfirmCancel();
        }

        modal.$promise.then(modal.show)
      }

      return deferred.promise;
    }
  }

}]);

/* jshint ignore:end */
