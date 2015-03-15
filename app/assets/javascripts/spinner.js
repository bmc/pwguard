/*
 ******************************************************************************
 Spinner component (service and directive)

 This code is ES6-based and must be transpiled with traceur. JSHint doesn't
 grok ES6, either, so it must be disabled for this file.

 WARNING: Do NOT include this file in the HTML. traceur automatically
 mixes it into the main Javascript file.
 ******************************************************************************
*/
var pwgSpinnerModule = angular.module('pwguard-spinner', []);

var templateURL   = window.angularTemplateURL;

// ----------------------------------------------------------------------------
// Simple spinner service. Works in conjunction with the "pwgSpinner"
// directive.
//
// This service provides the following functions:
//
// pwgSpinner.start() - start the spinner
// pwgSpinner.stop()  - stop the spinner
// ----------------------------------------------------------------------------

pwgSpinnerModule.factory('pwgSpinner', ng(function() {
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
// Spinner directive, used to create the spinner HTML. Used in conjunction
// with the pwgSpinner service.
// ----------------------------------------------------------------------------

pwgSpinnerModule.directive('pwgSpinner', ng(function($injector) {
  return {
    restrict: 'E',
    scope: {
    },
    templateUrl: templateURL('directives/pwgSpinner.html'),

    link: function($scope, element, attrs) {
      var pwgSpinner = $injector.get('pwgSpinner');
      var pwgLogging = $injector.get('pwgLogging');
      var pwgTimeout = $injector.get('pwgTimeout');
      var log        = pwgLogging.logger('pwgSpinner');

      $scope.showSpinner = false;
      pwgSpinner._registerDirective($scope);

      var modal = element.find(".modal");
      modal.modal({
        backdrop: 'static',
        keyboard: false,
        show:     false
      });

      var timerPromise = null;

      var show = () => { modal.modal('show'); }
      var hide = () => { modal.modal('hide'); }

      $scope.$watch("showSpinner", function() {
        // To prevent the spinner from flashing without actually being seen,
        // use a timeout to ensure that it's up at least 300 milliseconds
        // before we clear it.
        if ($scope.showSpinner) {
          show();
          // The timeout promise will contain the value of the function.
          log.debug("Starting timer...");
          timerPromise = pwgTimeout.timeout(500, function() {
            log.debug("Timeout promise fired.");
            return true;
          });
        }

        else {
          if (timerPromise)
            timerPromise.then(hide);
          else
            hide();
        }
      })
    }
  }
}));
