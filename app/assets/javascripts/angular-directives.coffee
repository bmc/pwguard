# Local Angular directives.

pwgDirectives = angular.module 'pwguard-directives', ['pwguard-services',
                                                      'ngAnimate']
templateURL   = window.angularTemplateURL

# -----------------------------------------------------------------------------
# Flash block
# -----------------------------------------------------------------------------

pwgFlash = ($compile, $rootScope) ->
  restrict:    'EA'
  transclude:  false
  templateUrl: templateURL('directives/pwgFlash.html')
  replace:     true
  scope:
    flashType: "@"

  link: (scope, element, attrs) ->
    scope.message = null

    $rootScope.$watch "flash.message.#{scope.flashType}", (newValue, oldValue) ->
      scope.message = newValue

    scope.clear = ->
      scope.message = null

pwgDirectives.directive 'pwgFlash', ['$compile',
                                     '$rootScope',
                                     pwgFlash]

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
