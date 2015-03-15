/*
 ******************************************************************************
 Flash component (service and directive)

 This code is ES6-based and must be transpiled with traceur. JSHint doesn't
 grok ES6, either, so it must be disabled for this file.

 WARNING: Do NOT include this file in the HTML. traceur automatically
 mixes it into the main Javascript file.
 ******************************************************************************
*/
var pwgFlashModule = angular.module('pwguard-flash', ['pwguard-services']);

var templateURL   = window.angularTemplateURL;

// ----------------------------------------------------------------------------
// Flash service. Hides underlying implementation(s).
//
// The service provides the following functions:
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

pwgFlashModule.factory('pwgFlash', ng(function(pwgTimeout) {

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
        pwgTimeout.timeout(timeout * 1000, function() { clearAlert(type) });
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

// -----------------------------------------------------------------------------
// Flash widget. Works in conjunction with the pwgFlash service.
//
// Usage:
//   <pwg-flash-widget type="[type]"></pwg-flash-widget>
//
//   [type] can be one of: error, warning, info
// -----------------------------------------------------------------------------

pwgFlashModule.directive('pwgFlash', ng(function(pwgFlash) {
  return {
    restrict:    'E',
    transclude:  false,
    replace:     false,
    templateUrl: templateURL('directives/pwgFlashWidget.html'),
    scope: {
      type: '@'
    },
    link: ($scope, element, attrs) => {

      $scope.message = null;

      switch ($scope.type) {
        case 'error':
          $scope.alertType = 'danger';
          break;
        case 'info':
          $scope.alertType = 'info';
          break;
        case 'warning':
          $scope.alertType = 'warning';
          break;
        default:
          throw new Error(`Unknown flash type: ${type}`)
      }

      var show = (msg) => { $scope.message = msg; }
      var hide = () => { $scope.message = null; }

      $scope.hide = hide;

      pwgFlash._registerFlashBox($scope.type, show, hide)
    }
  }
}));
