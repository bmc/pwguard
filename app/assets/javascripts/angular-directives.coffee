# Local Angular directives.

pwgDirectives = angular.module 'pwguard-directives', ['pwguard-services']
templateURL   = window.angularTemplateURL

# -----------------------------------------------------------------------------
# Sort indicator
# -----------------------------------------------------------------------------

pwgSortIndicator = ->
  restrict:    'E'
  transclude:  false
  replace:     true
  templateUrl: templateURL('directives/pwgSortIndicator.html')
  scope:
    reverse:    "="
    column:     "@"
    sortColumn: "="

pwgDirectives.directive 'pwgSortIndicator', [pwgSortIndicator]

# -----------------------------------------------------------------------------
# Fake checkbox, useful when you want to support clicking outside the checkbox
# itself
# -----------------------------------------------------------------------------

pwgFakeCheckbox = ->
  restrict:    'EA'
  transclude:  false
  replace:     true
  templateUrl: templateURL('directives/pwgFakeCheckbox.html')
  scope:
    ngModel: "=ngModel"

pwgDirectives.directive 'pwgFakeCheckbox', [pwgFakeCheckbox]

# ----------------------------------------------------------------------------
# Mark an input element as selected when clicked. Must be used as an
# attribute.
# ----------------------------------------------------------------------------

pwgSelectOnClick = ->
  restrict:   'A'
  transclude: false
  replace:    false
  scope:      null

  link: (scope, element, attrs) ->
    element.click ->
      this.select()

pwgDirectives.directive 'pwgSelectOnClick', [pwgSelectOnClick]

# ----------------------------------------------------------------------------
# Add a bootstrap error class to a form-group. Requires the existence of an
# outer form. Use like this:
#
# <form name="someForm" ng-submit="submitForm()" novalidate">
#   <div class="form-group" pwg-bootstrap-form-error="someForm.name">
#     <label class="control-label" for="name">Name</label>
#     <input class="form-control" id="name" name="name" type="text"
#            ng-required="true" placeholder="Name">
#     <p class="help-block" ng-show="someForm.name.$error.required && someForm.name.$dirty">
#       Name is required.
#     </p>
#   </div>
#   ...
# </form>
# ----------------------------------------------------------------------------

pwgBootstrapFormError = ->
  restrict: 'A'
  require: '^form'
  scope:
    pwgBootstrapFormError: "@"

  link: (scope, element, attrs, ctrl) ->
    field = ctrl[scope.pwgBootstrapFormError]
    watchFunc = ->
      field.$invalid && field.$dirty

    scope.$watch watchFunc, (newValue) ->
      if newValue
        element.addClass 'has-error'
      else
        element.removeClass 'has-error'

pwgDirectives.directive 'pwgBootstrapFormError', [pwgBootstrapFormError]

# ----------------------------------------------------------------------------
# Allow a drag-and-drop of a file, posting the results to a callback as
# Base64. This approach allows the file to be uploaded as JSON, which
# bypasses problems with direct file upload. (Nginx has problems handling
# file uploads and passing them along via a reverse proxy.)
# ----------------------------------------------------------------------------

# Adapted from http://jsfiddle.net/lsiv568/fsfPe/10/ and
# http://buildinternet.com/2013/08/drag-and-drop-file-upload-with-angularjs/
#
# Attributes:
#   pwg-drop-file: List of MIME types of allowed files, or "" for anything.
#   max-file-size: Maximum file size, in megabytes. Empty or 0 means unlimited.
#   fileDropped:   Name of function on scope to call when a file is dropped
#                  onto the control. The function is called with the file
#                  contents as a Base64 string, the file name, and the MIME
#                  type.
#   onError:       Optional name of function to be called when an error occurs.
#                  Called with an error message.
pwgDropFile = ->
  restrict: 'A'
  scope:
    maxFileSize: "@"
    fileDropped: '='
    onError:     '='

  link: (scope, element, attrs, ngModel) ->

    getEvent = (e) ->
      if e.dataTransfer?
        e
      else if e.originalEvent?.dataTransfer?
        e.originalEvent
      else
        null

    processDragOverOrEnter = (event) ->
      e = getEvent(event)
      e?.preventDefault()

      e?.dataTransfer?.effectAllowed = 'copy'
      element.addClass('drag-over')
      false

    processDragLeave = (event) ->
      if event?
        event.preventDefault()
      element.removeClass('drag-over')
      false

    validMimeTypes = null
    if attrs.pwgDropFile?
      validMimeTypes = attrs.pwgDropFile.split(",").map (f) -> f.trim()

    if attrs.maxFileSize
      maxFileSize = parseInt attrs.maxFileSize
      if isNaN(maxFileSize)
        throw "Bad value for max-file-size: #{attrs.maxFileSize}"
    else
      maxFileSize = 0

    sizeIsOkay = (size) ->
      if (maxFileSize is 0) || (((size / 1024) / 1024) <= maxFileSize)
        true
      else
        if scope.onError?
          scope.onError("File must be #{maxFileSize} MB or smaller.")
        false

    typeIsValid = (type) ->
      if validMimeTypes?
        ok = validMimeTypes.indexOf(type) > -1
        unless ok
          if scope.onError?
            scope.onError("Incorrect file type.")
        ok
      else
        true

    arrayBufferToBase64 = (buf) ->
      res = ''
      bytes = new Uint8Array(buf)
      len = bytes.byteLength
      for b in bytes
        res += String.fromCharCode(b)

      window.btoa(res)

    element.bind 'dragover', processDragOverOrEnter
    element.bind 'dragenter', processDragOverOrEnter
    element.bind 'dragleave', processDragLeave
    element.bind 'drop', (event) ->
      e = getEvent(event)
      e?.preventDefault()
      reader = new FileReader()
      file   = e?.dataTransfer.files[0]
      name   = file.name
      type   = file.type
      size   = file.size
      reader.onload = (evt) ->
        if sizeIsOkay(size) and typeIsValid(type)
          scope.$apply ->
            file = evt.target.result
            base64 = arrayBufferToBase64(file)
            scope.fileDropped(base64, name, type) if scope.fileDropped?
            if angular.isString(scope.fileName)
              scope.fileName = name

      reader.readAsArrayBuffer(file)
      processDragLeave()
      false

pwgDirectives.directive 'pwgDropFile', [pwgDropFile]
