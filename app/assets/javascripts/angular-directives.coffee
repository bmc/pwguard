# Local Angular directives.

pwgDirectives = angular.module 'pwguard-directives', ['pwguard-services']
templateURL   = window.angularTemplateURL

# -----------------------------------------------------------------------------
# Flash block
# -----------------------------------------------------------------------------

pwgDirectives.directive 'pwgFlash', ($compile, pwgFlash, $rootScope) ->
  restrict:    'EA'
  transclude:  false
  templateUrl: templateURL('pwgFlash.html')
  replace:     true
  scope:
    flashType: "@"

  link: (scope, element, attrs) ->
    scope.message = null

    $rootScope.$watch "flash.message.#{scope.flashType}", (newValue) ->
      scope.message = newValue

    scope.clear = ->
      scope.message = null

