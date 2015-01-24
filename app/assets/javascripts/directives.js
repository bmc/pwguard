/*
  Angular directives.

  This code is ES6-based and must be transpiled with traceur. JSHint doesn't
  grok ES6, either, so it must be disabled for this file.

  WARNING: Do NOT include this file in the HTML. sbt-traceur automatically
  mixes it into the main Javascript file.
*/

/* jshint ignore:start */

var pwgDirectives = angular.module('pwguard-directives', ['pwguard-services']);

var templateURL   = window.angularTemplateURL;

// -----------------------------------------------------------------------------
// Sort indicator
// -----------------------------------------------------------------------------

pwgDirectives.directive('pwgSortIndicator', function() {
  return {
    restrict:    'E',
    transclude:  false,
    replace:     true,
    templateUrl: templateURL('directives/pwgSortIndicator.html'),
    scope: {
      reverse:    '=',
      column:     '@',
      sortColumn: '='
    }
  }
});

// -----------------------------------------------------------------------------
// Fake checkbox, useful when you want to support clicking outside the checkbox
// itself
// -----------------------------------------------------------------------------

pwgDirectives.directive('pwgFakeCheckbox', function() {
  return {
    restrict:    'EA',
    transclude:  false,
    replace:     true,
    templateUrl: templateURL('directives/pwgFakeCheckbox.html'),
    scope: {
      ngModel: '=ngModel'
    }
  }
});

// ----------------------------------------------------------------------------
// Mark an input element as selected when clicked. Must be used as an
// attribute.
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgSelectOnClick', function() {
  return {
    restrict:   'A',
    transclude: false,
    replace:    false,
    scope:      null,

    link: function(scope, element, attrs) {
      element.click(function() {
        this.select();
      });
    }
  }
});

// ----------------------------------------------------------------------------
// Add a bootstrap error class to a form-group. Requires the existence of an
// outer form. Use like this:
//
// <form name="someForm" ng-submit="submitForm()" novalidate">
//   <div class="form-group" pwg-bootstrap-form-error="someForm.name">
//     <label class="control-label" for="name">Name</label>
//     <input class="form-control" id="name" name="name" type="text"
//            ng-required="true" placeholder="Name">
//     <p class="help-block" ng-show="someForm.name.$error.required && someForm.name.$dirty">
//       Name is required.
//     </p>
//   </div>
//   ...
// </form>
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgBootstrapFormError', function() {
  return {
    restrict: 'A',
    require: '^form',
    scope: {
      pwgBootstrapFormError: "@"
    },

    link: function(scope, element, attrs, ctrl) {
      var field = ctrl[scope.pwgBootstrapFormError];
      var watchFunc = function() {
        return field.$invalid && field.$dirty;
      }

      scope.$watch(watchFunc, function(newValue) {
        if (newValue)
          element.addClass('has-error');
        else
          element.removeClass('has-error');
      });
    }
  }
});


// ----------------------------------------------------------------------------
// Allow a drag-and-drop of a file, posting the results to a callback as
// Base64. This approach allows the file to be uploaded as JSON, which
// bypasses problems with direct file upload. (Nginx has problems handling
// file uploads and passing them along via a reverse proxy.)
// ----------------------------------------------------------------------------

// Adapted from http://jsfiddle.net/lsiv568/fsfPe/10/ and
// http://buildinternet.com/2013/08/drag-and-drop-file-upload-with-angularjs/
//
// Attributes:
//   pwg-drop-file: List of MIME types of allowed files, or "" for anything.
//   max-file-size: Maximum file size, in megabytes. Empty or 0 means unlimited.
//   fileDropped:   Name of function on scope to call when a file is dropped
//                  onto the control. The function is called with the file
//                  contents as a Base64 string, the file name, and the MIME
//                  type.
//   onError:       Optional name of function to be called when an error occurs.
//                  Called with an error message.

pwgDirectives.directive('pwgDropFile', ['pwgLogging', function(pwgLogging) {
  var logger = pwgLogging.logger('pwgDropFile');

  return {
    restrict: 'A',
    scope: {
      maxFileSize: '@',
      fileDropped: '=',
      onError:     '=',
    },

    link: function(scope, element, attrs, ngModel) {

      function getEvent(e) {
        var res = null;
        if (e.dataTransfer)
          res = e;
        else if (e.originalEvent && e.originalEvent.dataTransfer)
          res = e.originalEvent;

        return res;
      }

      function processDragOverOrEnter(event) {
        var e = getEvent(event);
        if (e) e.preventDefault();

        // As noted at http://stackoverflow.com/a/27256625/53495, "if you
        // decide to set the the effectAllowed and dropEffect in the dragstart
        // event handler, you need to also set the dropEffect in the dragenter
        // and dragover event handlers. Failing to do so will prevent the drop
        // event from firing."
        //
        // It's easier to skip the setting altogether.
        // e.dataTransfer.effectAllowed = 'copy'

        element.addClass('drag-over');
        return false;
      }

      function processDragLeave(event) {
        if (event)
          event.preventDefault();

        element.removeClass('drag-over');
        return false;
      }

      var validMimeTypes = null;
      if (attrs.pwgDropFile)
        validMimeTypes = _.map(attrs.pwgDropFile.split(','),
                               (f) => { return f.trim(); });

      var maxFileSize = 0;
      if (attrs.maxFileSize) {
        maxFileSize = parseInt(attrs.maxFileSize);
        if (isNaN(maxFileSize))
          throw `Bad value for max-file-size: ${attrs.maxFileSize}`
      }

      function sizeIsOkay(size) {
        if ((maxFileSize == 0) || (((size / 1024) / 1024) <= maxFileSize))
          return true;
        else {
          if (scope.onError)
            scope.onError(`File must be ${maxFileSize} MB or smaller.`)
          return false;
        }
      }

      function typeIsValid(type) {
        var ok = true;
        if (validMimeTypes)
          ok = (validMimeTypes.indexOf(type) > -1);

        if (! ok) {
          if (scope.onError)
            scope.onError("Incorrect file type.")
        }

        return ok;
      }

      function arrayBufferToBase64(buf) {
        var res = '';
        var bytes = new Uint8Array(buf);
        var len = bytes.byteLength;
        for (var i = 0; i < len; i++) {
          res += String.fromCharCode(bytes[i]);
        }
        return window.btoa(res);
      }

      element.bind('dragover', processDragOverOrEnter);
      element.bind('dragenter', processDragOverOrEnter);
      element.bind('dragleave', processDragLeave);
      element.bind('drop', function(event) {
        var e = getEvent(event);
        if (! e) {
          logger.error("No event posted on file drop.");
          return;
        }

        if (! e.dataTransfer) {
          logger.error("No dataTransfer object in file-drop event.");
          return;
        }

        e.preventDefault();
        var file = e.dataTransfer.files[0]
        if (! file) {
          logger.error("No file(s) in file drop event.");
          return;
        }

        var reader = new FileReader();
        var name   = file.name;
        var type   = file.type;
        var size   = file.size;

        reader.onload = function(e) {
          if (sizeIsOkay(size) && typeIsValid(type)) {
            scope.$apply(function() {
              var buf = e.target.result;
              var base64 = arrayBufferToBase64(buf);
              if (scope.fileDropped)
                scope.fileDropped(base64, name, type);
            });
          }
        }

        reader.readAsArrayBuffer(file);
        processDragLeave();
        return false;
      });
    }
  }
}]);

// ----------------------------------------------------------------------------
// Allow setting a form field's name from a model.
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgName', ['$injector', function($injector) {

  var $compile = $injector.get('$compile');
  return {
    restrict: 'A',
    require:  '^form',
    scope: {
      pwgName: '='
    },
    link: function($scope, element, attrs) {
      $scope.$watch('pwgName', function(newValue) {
        let n = newValue;
        if (!n) n = "";
        console.log(`pwg-name: name=${n}`);
        element.attr("name", n);
      });
    }
  }

}]);

// ----------------------------------------------------------------------------
// Display and manage a password entry edit form
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgEditPasswordEntryForm',
   ['$injector', function($injector) {

     var pwgAjax       = $injector.get('pwgAjax');
     var pwgLogging    = $injector.get('pwgLogging');
     var pwgFormHelper = $injector.get('pwgFormHelper');
     var pwgFlash      = $injector.get('pwgFlash');
     var pwgRoutes     = $injector.get('pwgRoutes');
     var name          = 'pwg-edit-password-entry-form';
     var log           = pwgLogging.logger('pwgEditPasswordEntryForm');

     return {
       restrict: 'E',
       scope: {
         ngModel:     '=',
         saveUrl:     '=',
         onSave:      '=',
         cancelRoute: '@',
         saveRoute:   '@'
       },
       templateUrl: templateURL('directives/pwgEditPasswordEntryForm.html'),

       link: function($scope, element, attrs) {
         if (! attrs.ngModel)
           throw new Error(`${name}: ng-model attribute is required.`);
         if (! attrs.cancelRoute)
           throw new Error(`${name}: cancel-route attribute is required.`);
         if (! attrs.saveUrl)
           throw new Error(`${name}: save-url attribute is required.`);

         function setFormValidity(valid) {
           $scope.entryForm.$valid   = valid;  // hack
           $scope.entryForm.$invalid = !valid; // hack
         }

         function setFormDirtyFlag(dirty) {
           if (dirty)
             $scope.entryForm.$setDirty();
           else
             $scope.entryForm.$setPristine();
         }

         function augmentExtra(index, extra) {
           _.extend(extra, {
             deleted:        false,
             delete:         function() {
               extra.deleted = true;
               setFormDirtyFlag(true);
             },
             inputNameName:  `extraFieldName${index}`,
             inputValueName: `extraFieldValue${index}`,
             originalName:   extra.fieldName,
             originalValue:  extra.fieldValue,
             isValid:        function() {
               let v = (extra.fieldName != null) &&
                       (extra.fieldName.trim().length > 0) &&
                       (extra.fieldValue != null) &&
                       (extra.fieldValue.trim().length > 0);
               return v;
             }
           });

           return extra;
         }

         $scope.$watch('ngModel', function(newValue) {
           if (newValue) {
             log.debug(`ngModel: ${JSON.stringify(newValue)}`);
             if (! newValue.extras) newValue.extras = [];

             for (let i = 0; i < newValue.extras.length; i++) {
               newValue.extras[i] = augmentExtra(i, newValue.extras[i]);
             }

             log.debug(`entry is now: ${JSON.stringify(newValue)}`);
           }
         });

         // When using the "filter" filter with a function, the specified function
         // is a constructor that returns that actual filter function.
         $scope.fieldNotDeleted = function() {
           return function(extra) { return !extra.deleted; }
         }

         $scope.addExtra = function() {
           let i = $scope.ngModel.extras.length
           let extra = augmentExtra(i, {
             fieldName:      null,
             fieldValue:     null,
             id:             null
           });
           $scope.ngModel.extras.push(extra);
           setFormDirtyFlag(true);
           $scope.checkExtraField(extra);
         }

         $scope.cancel = function() {
           pwgFormHelper.validateCancellation($scope.entryForm,
                                              $scope.cancelRoute);
         }

         $scope.checkExtraField = function(extra) {
           // Save the current form status.
           let formOrigState = formIsValid();
           let formValid = true;

           // Check the extra.
           if (extra.isValid()) {
             formValid = formOrigState;
           }

           else {
             formValid = false;
           }

           // It'd be nice if (a) Angular handled dynamically-added form fields
           // properly (it doesn't), or (b) it provided a better mechanism for
           // marking a form valid/invalid.
           setFormValidity(formValid);

           if ((extra.fieldName != extra.originalName) ||
              (extra.fieldValue != extra.originalValue)) {
             $scope.entryForm.$setDirty();
           }

         }

         function formIsValid() {
           // Check the validity of non-extra (i.e., non-dynamic) form
           // elements. This is a hack, but it's necessary because Angular
           // doesn't properly handle dynamically-added form fields.
           var valid = true;
           for (let key in $scope.entryForm) {
             if ($scope.entryForm.hasOwnProperty(key) &&
                 $scope.entryForm.$addControl) {
               let field = $scope.entryForm[key];
               if (field.$dirty && field.$invalid) {
                 valid = false;
                 break;
               }
             }
           }

           if (valid) {
             // check the dynamic fields.
             for (let extra of $scope.ngModel.extras) {
               if (! extra.isValid()) {
                 valid = false;
                 break;
               }
             }
           }

           return valid;
         }

         $scope.submit = function() {
           let id = $scope.ngModel.id;
           let name = $scope.ngModel.name;
           $scope.ngModel.extras = _.filter($scope.ngModel.extras, function(e) {
             return ! e.deleted;
           });
           log.debug(`Saving password entry. name=${name}, url=${$scope.saveUrl}`);
           pwgAjax.post($scope.saveUrl, $scope.ngModel, function() {
             pwgFlash.info("Saved.", 5 /* second timeout */);
             if ($scope.onSave)
               $scope.onSave($scope.ngModel);
             if ($scope.saveRoute)
               pwgRoutes.redirectToNamedRoute($scope.saveRoute);
           });
         }
       }
     }
   }
]);

/* jshint ignore:end */
