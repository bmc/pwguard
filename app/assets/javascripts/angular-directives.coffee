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


    # DON'T ADD THE "hide" CLASS UPON INITIALIZATION! Doing so will cause
    # Angular to run the animation when the element is initially hidden.
    # Instead, add it the first time through.

    #if attrs.animateHide?
      #element.addClass "animate-hide"

    if attrs.animateShow?
      element.addClass "animate-show"

    scope.message = null

    $rootScope.$watch "flash.message.#{scope.flashType}", (newValue, oldValue) ->
      if newValue?
        # We have a value. Add the animation class.
        element.addClass "animate-hide"

      scope.message = newValue

    scope.clear = ->
      scope.message = null

pwgDirectives.directive 'pwgFlash', ['$compile',
                                     '$rootScope',
                                     pwgFlash]

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

  link: (scope, element, attrs) ->
    return

pwgDirectives.directive 'pwgFakeCheckbox', [pwgFakeCheckbox]
