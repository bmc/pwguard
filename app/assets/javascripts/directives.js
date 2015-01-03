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

/* jshint ignore:end */