/*
 ******************************************************************************
  Miscellaneous Angular directives not otherwise grouped.

  This code is ES6-based and must be transpiled with traceur. JSHint doesn't
  grok ES6, either, so it must be disabled for this file.

  WARNING: Do NOT include this file in the HTML. traceur automatically
  mixes it into the main Javascript file.
 ******************************************************************************
*/

/* jshint ignore:start */

var pwgDirectives = angular.module('pwguard-directives',
                                   ['pwguard-services', 'ngTagsInput']);

var templateURL   = window.angularTemplateURL;

// ----------------------------------------------------------------------------
// Navbar button
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgTabLabel', function() {
  return {
    restrict:    'E',
    transclude:  false,
    replace:     true,
    templateUrl: templateURL('directives/pwgTabLabel.html'),
    scope: {
      icon: '@',
      text: '@'
    },
    link: function(scope, element, attrs) {
      scope.isMobile = window.browserIsMobile;
    }
  }
});

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
// Password display widget.
// -----------------------------------------------------------------------------

pwgDirectives.directive('pwgPasswordDisplay', function() {
  return {
    restrict:    'E',
    transclude:  false,
    replace:     true,
    templateUrl: templateURL('directives/pwgPasswordDisplay.html'),
    scope: {
      plaintextPassword: "@"
    },
    link: function(scope, element, attrs) {
      if (! attrs.plaintextPassword) {
        throw new Error("plaintext-password attribute is required.");
      }

      scope.passwordIsVisible = false;
      scope.togglePasswordVisibility = () => {
        scope.passwordIsVisible = !scope.passwordIsVisible;
      }
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

// -----------------------------------------------------------------------------
// Progress bar.
//
// Usage:
//   <pwg-progress-bar ng-model="[percent]" show="[show]"></pwg-progress-bar>
//
// [percent] is a model containing the percentage, as an integer (e.g., 9, 95)
// [show] is an expression which, if truthy, shows the progress bar
// -----------------------------------------------------------------------------

pwgDirectives.directive('pwgProgressBar', function() {
  return {
    restrict:    'E',
    transclud:   false,
    replace:     false,
    templateUrl: templateURL('directives/pwgProgressBar.html'),
    scope: {
      ngModel: '=',
      show:    '&'
    },

    link: function($scope, element, attrs) {
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
      function watchFunc() {
        var field = ctrl[scope.pwgBootstrapFormError];
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
// A popover directive, hiding the actual implementation. Using this directive
// isolates the views from the actual implementation.
//
// Usage:
//    <pwg-popover title="[title]"
//                 icon="[icon]"
//                 placement="[placement]"
//                 content="[content]"
//                 trigger="[click]">
//    </pwg-popover>
//
//    [title]     - title for the popover. OPTIONAL.
//    [icon]      - FontAwesome icon (e.g., "fa-question-circle"). REQUIRED.
//    [placement] - Popover placement, one of "top", "bottom", "right", or
//                  "left". REQUIRED.
//    [trigger]   - Trigger action, one of "click", "hover" or "focus".
//                  REQUIRED.
//    [content]   - Text to display in the popup. REQUIRED.
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgPopover', function() {
  function requiredAttr(attrs, name) {
    if (! attrs[name]) {
      throw `pwg-popover: ${name} attribute is required.`
    }
  }

  return {
    restrict:     'E',
    replace:      true,
    transclude:   true,
    templateUrl:  templateURL('directives/pwgPopover.html'),
    scope: {
      title:     "@",
      icon:      "@",
      placement: "@",
      trigger:   "@",
      content:   "@"
    },
    link: function(scope, element, attrs) {
      requiredAttr(scope, 'placement');
      requiredAttr(scope, 'icon');
      requiredAttr(scope, 'trigger');
      requiredAttr(scope, 'content');

      let popover = element.find('.popover-button');

      popover.popover({
        trigger:   scope.trigger,
        content:   scope.content
      });
    }
  }
});

// ----------------------------------------------------------------------------
// Put a tooltip on a clickable item, such as a button.
//
// This directive is an attribute, and it looks for other attributes.
//
// pwg-tooltip           - marks a item as having a tooltip
// pwg-tooltip-title     - the tooltip title (text). REQUIRED.
// pwg-tooltip-placement - placement, one of "top", "button", "left",
//                         "right" or "auto". OPTIONAL. Default: "top".
// pwg-tooltip-trigger   - tooltip trigger, one of "hover", "click", or "focus".
//                         OPTIONAL. Default: "hover".
// pwg-tooltip-delay     - delay before showing, in milliseconds. OPTIONAL.
//                         Default: 0.
// pwg-tooltip-animation - whether or not to animate the tooltip. OPTIONAL.
//                         True if present, else false.
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgTooltip', ng(function() {
  return {
    restrict: 'A',
    replace:  false,
    scope: {
      pwgTooltipTitle:     '@',
      pwgTooltipPlacement: '@',
      pwgTooltipTrigger:   '@',
      pwgTooltipDelay:     '@'
    },
    link: ($scope, element, attrs) => {
      if (! attrs.pwgTooltipTitle)
        throw new Error("pwg-tooltip must have a pwg-tooltip-title");

      var options = {
        title:     attrs.pwgTooltipTitle,
        placement: attrs.pwgTooltipPlacement || 'top',
        trigger:   attrs.pwgTooltipTrigger || 'hover',
        delay:     parseInt(attrs.pwgTooltipDelay) || 0,
        animation: attrs.pwgTooltipAnimation ? true : false
      }

      element.tooltip(options);
    }
  }
}));

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
//                  onto the control. How this function is called depends on
//                  the "raw" attribute. If "raw" is:
//
//                  truthy - the function is called with the file object (one
//                           argument)
//                  falsy  - the function is called with the file contents as
//                           a Base64 string, the file name, and the MIME type
//                           (three arguments).
//
//   onError:       Optional name of function to be called when an error occurs.
//                  Called with an error message.
//   raw:           Optional. If true, file is not converted to Base64.

pwgDirectives.directive('pwgDropFile', ng(function(pwgLogging) {
  var logger = pwgLogging.logger('pwgDropFile');

  return {
    restrict: 'A',
    scope: {
      maxFileSize: '@',
      fileDropped: '=',
      onError:     '=',
      raw:         '&'
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
        if ((maxFileSize === 0) || (((size / 1024) / 1024) <= maxFileSize))
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

        if (scope.raw()) {
          if (scope.fileDropped) {
            scope.$apply(() => { scope.fileDropped(file); });
          }
        }
        else {
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
        }

        processDragLeave();
        return false;
      });
    }
  }
}));

// ----------------------------------------------------------------------------
// Allow setting a form field's name from a model.
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgName', ng(function($injector) {

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
        element.attr("name", n);
      });
    }
  }
}));

// ----------------------------------------------------------------------------
// Display and manage a password entry edit form
// ----------------------------------------------------------------------------

pwgDirectives.directive('pwgEditPasswordEntryForm', ng(function($injector) {

  var pwgAjax       = $injector.get('pwgAjax');
  var pwgLogging    = $injector.get('pwgLogging');
  var pwgFormHelper = $injector.get('pwgFormHelper');
  var pwgFlash      = $injector.get('pwgFlash');
  var pwgRoutes     = $injector.get('pwgRoutes');
  var pwgForm       = $injector.get('pwgForm');
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

      var dirtyOnLoad = false;
      if (attrs.dirtyOnLoad) dirtyOnLoad = eval(attrs.dirtyOnLoad);

      pwgForm.setDirty($scope.entryForm, dirtyOnLoad);

      function augmentExtra(index, extra) {
        _.assign(extra, {
          deleted:        false,
          inputNameName:  `extraFieldName${index}`,
          inputValueName: `extraFieldValue${index}`,
          originalName:   extra.fieldName,
          originalValue:  extra.fieldValue,
          isValid:        () => {
            return extra.fieldName && extra.fieldValue;
          },
          delete:         () => {
            extra.deleted = true;
            pwgForm.setDirty($scope.entryForm, true);
          }
        });

        return extra;
      }

      function augmentSecurityQuestion(index, q) {
        _.assign(q, {
          deleted:          false,
          inputNameName:    `inputQuestionName${index}`,
          inputValueName:   `inputAnswerName${index}`,
          originalQuestion: q.question,
          originalAnswer:   q.answer,
          isValid:          () => {
            return q.question && q.answer;
          },
          delete:           () => {
            q.deleted = true;
            pwgForm.setDirty($scope.entryForm, true);
          }
        });

        return q;
      }

      let allUniqueKeywords = null;

      $scope.loadUniqueKeywords = (query) => {
        let $q = $injector.get('$q');
        let deferred = $q.defer();
        let url = routes.controllers.PasswordEntryController.getUniqueKeywords().url;

        function filterAndResolve(uniqueKeywordStrings) {
          let q = query.toLowerCase();
          deferred.resolve(_.filter(uniqueKeywordStrings, (k) => {
            return _.startsWith(k.toLowerCase(), q);
          }));
        }

        if (allUniqueKeywords) {
          // Already loaded.
          filterAndResolve(allUniqueKeywords);
        }
        else {
          pwgAjax.get(url, false).then(function(response) {
            allUniqueKeywords = response.data.keywords;
            filterAndResolve(allUniqueKeywords);
          });
        }

        return deferred.promise;
      }

      $scope.$watch('ngModel', function(v) {
        if (v) {
          log.debug(`ngModel: ${JSON.stringify(v)}`);
          if (! v.extras) v.extras = [];
          if (! v.keywords) v.keywords = [];

          for (let i = 0; i < v.extras.length; i++) {
            v.extras[i] = augmentExtra(i, v.extras[i]);
          }

          for (let i = 0; i < v.securityQuestions.length; i++) {
            v.securityQuestions[i] = augmentSecurityQuestion(i, v.securityQuestions[i]);
          }

          // Filter out any empty keywords that happen to be posted.
          v.keywords = _.filter(v.keywords, (k) => {
            return k.keyword && k.keyword.trim().length > 0
          });

          // Convert the keywords into ngTagsInput-compatible objects.
          // We'll have to map these back to PWGuard-compatible keyword
          // objects when we post.
          $scope.keywordTags = _.map(v.keywords, (k) => {
            return { text: k.keyword }
          });

          log.debug(`entry is now: ${JSON.stringify(v)}`);
        }
      });

      // When using the "filter" filter with a function, the specified function
      // is a constructor that returns that actual filter function.
      $scope.fieldNotDeleted = function() {
        return function(extra) { return !extra.deleted; }
      }

      $scope.questionNotDeleted = function() {
        return function(q) { return !q.deleted; }
      }

      $scope.addExtra = function() {
        let i = $scope.ngModel.extras.length
        let extra = augmentExtra(i, {
          fieldName:      null,
          fieldValue:     null,
          isPassword:     false,
          id:             null
        });
        $scope.ngModel.extras.push(extra);
        pwgForm.setDirty($scope.entryForm, true);
        $scope.checkExtraField(extra);
      }

      $scope.addSecurityQuestion = function() {
        let i = $scope.ngModel.securityQuestions.length;
        let q = augmentSecurityQuestion(i, {
          question: null,
          answer:   null,
          id:       null
        });
        $scope.ngModel.securityQuestions.push(q);
        pwgForm.setDirty($scope.entryForm, true);
        $scope.checkSecurityQuestion(q);
      }

      $scope.cancel = function() {
       pwgFormHelper.validateCancellation($scope.entryForm,
                                          $scope.cancelRoute);
      }

      function checkField(thing) {
        // Save the current form status.
        let formOrigState = formIsValid();
        let formValid     = true;

        // Check the extra.
        if (thing.isValid())
          formValid = formOrigState;
        else
          formValid = false;

        // It'd be nice if (a) Angular handled dynamically-added form fields
        // properly (it doesn't), or (b) it provided a better mechanism for
        // marking a form valid/invalid.
        pwgForm.setValid($scope.entryForm, formValid);
        return formValid;
      }

      $scope.checkExtraField = function(extra) {
        checkField(extra);

        if ((extra.fieldName !== extra.originalName) ||
            (extra.fieldValue !== extra.originalValue)) {
          pwgForm.setDirty($scope.entryForm, true);
        }
      }

      $scope.checkSecurityQuestion = function(q) {
        checkField(q);
        if ((q.question !== q.originalQuestion) ||
            (q.answer   !== q.originalAnswer)) {
          pwgForm.setDirty($scope.entryForm, true);
        }
      }

      function formIsValid() {
        // Check the validity of non-extra (i.e., non-dynamic) form
        // elements. This is a hack, but it's necessary because Angular
        // doesn't properly handle dynamically-added form fields.
        var valid    = true;
        var formKeys = _.keys($scope.entryForm.length)
        var form     = $scope.entryForm;
        for (let i = 0; i < formKeys.length; i++) {
          let key = formKeys[i];
          if (form.hasOwnProperty(key) && form.$addControl) {
            let field = form[key];
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

      // Take the keyword strings, possibly modified in the DOM, and map
      // them back to keyword objects.
      function mapKeywordTags(pwEntry) {
        let kwMap = {};
        for (let kw of $scope.ngModel.keywords) {
          kwMap[kw.keyword] = kw;
        }

        let newKeywords = [];
        for (let k of $scope.keywordTags) {
          let kw = kwMap[k.text];
          if (kw) {
            // Existing tag. Preserve it.
            newKeywords.push(kw);
          }
          else {
            // New tag. Make it, with no ID.
            newKeywords.push({id: null, keyword: k.text});
          }
        }

        pwEntry.keywords = newKeywords;
        return pwEntry;
      }

      $scope.submit = function() {
        let id = $scope.ngModel.id;
        let name = $scope.ngModel.name;
        $scope.ngModel.extras = _.filter($scope.ngModel.extras, function(e) {
          return ! e.deleted;
        });

        // Map the keyword strings to their actual counterparts, if possible.
        // If not, create new ones.
        mapKeywordTags($scope.ngModel);

        log.debug(`Saving password entry. name=${name}, url=${$scope.saveUrl}`);
        // Make sure the extra fields and security questions have no leftovers.
        var data = $scope.ngModel;
        data.extras= _.filter(data.extras,
                              (ex) => { return ex.name !== null; });
        data.securityQuestions = _.filter(data.securityQuestions,
                                          (q) => { return q.question !== null });
        log.debug(JSON.stringify(data));
        pwgAjax.post($scope.saveUrl, data).then(function() {
          pwgFlash.info("Saved.", 5 /* second timeout */);
          if ($scope.onSave)
            $scope.onSave($scope.ngModel);
          if ($scope.saveRoute)
            pwgRoutes.redirectToNamedRoute($scope.saveRoute);
        });
      }
    }
  }
}));

/* jshint ignore:end */
