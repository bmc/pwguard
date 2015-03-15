/*
 ******************************************************************************
 Modal component (service and directive)

 This code is ES6-based and must be transpiled with traceur. JSHint doesn't
 grok ES6, either, so it must be disabled for this file.

 WARNING: Do NOT include this file in the HTML. traceur automatically
 mixes it into the main Javascript file.
 ******************************************************************************
*/
var pwgModalModule = angular.module('pwguard-modal', []);

var templateURL   = window.angularTemplateURL;

// ----------------------------------------------------------------------------
// Modal service. Hides underlying implementation(s).
//
// The service provides the following functions:
//
// pwgModal.confirm(message, title=null)
//
//     Show a configuration dialog. This function returns a $q promise. If
//     the user confirms the operation, the promise is resolved; otherwise,
//     the promise is rejected.
// ----------------------------------------------------------------------------

pwgModalModule.factory('pwgModal', ng(function($injector) {

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

// -----------------------------------------------------------------------------
// Modal widget. Works in conjunction with modal service.
//
// Usage:
//
// <pwg-modal-confirmation></pwg-modal-confirmation>
// -----------------------------------------------------------------------------

pwgModalModule.directive('pwgModalConfirmation', ng(function(pwgModal) {

  return {
    restrict:   'E',
    transclude: false,
    replace:    false,
    templateUrl: templateURL('directives/pwgModalConfirmation.html'),
    scope: {
    },
    link: ($scope, element, attrs) => {

      $scope.message = null;
      $scope.title   = null;

      var modalElement = element.children(".pwg-modal");

      var show = (message, title=null) => {
        $scope.message = message;
        $scope.title   = title;
        modalElement.modal('show');
      }

      $scope.keyPressed = (event) => {
        if (event.keyCode === 13) // ENTER
          $scope.cancel();
      }

      // The register function returns an object with an onOK() and onCancel()
      // function.
      var callbacks = pwgModal._registerModal(show);

      $scope.ok = () => {
        if (callbacks.onOK) callbacks.onOK();
        modalElement.modal('hide');
      }

      $scope.cancel = () => {
        if (callbacks.onCancel) callbacks.onCancel();
        modalElement.modal('hide');
      }
    }
  }

}));
