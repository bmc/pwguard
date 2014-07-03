###############################################################################
# Some non-Angular jQuery things.
###############################################################################

###############################################################################
# Angular JS stuff
###############################################################################

requiredModules = ['ngRoute',
                   'route-segment',
                   'view-segment',
                   'ngCookies',
                   'ui.bootstrap',
                   'ui.utils',
                   'tableSort',
                   'localytics.directives',
                   'http-auth-interceptor',
                   'pwguard-services']

# Angular.js configuration function. Passed into the actual application, below,
# when it is created.
configApp = ($routeSegmentProvider,
             $routeProvider,
             $locationProvider) ->

  # For now, don't use this. Allow Angular to use its "#" URLs. This approach
  # simplifies things on the backend, since it doesn't result in backend
  # routing issues. (That is, the Play! router ignores hash fragments.)

  #$locationProvider.html5Mode(true).hashPrefix('!')

  window.setRoutes $routeSegmentProvider, $routeProvider

# Initialize the application by storing some data and functions into the
# root scope. Invoked when the app is defined, below.
initApp = ($rootScope,
           $http,
           $routeSegment,
           $location,
           $timeout,
           pwgAjax,
           pwgFlash) ->

  console.log pwgFlash
  $rootScope.loggedInUser  = null
  $rootScope.$routeSegment = $routeSegment
  $rootScope.segmentOnLoad = window.segmentForURL($location.path())

  pwgFlash.init()
  pwgFlash.error("Test")

  $rootScope.$watch "loggedInUser", (user, prevUser) ->
    if user?
      segment = $rootScope.segmentOnLoad
      if segment?
        # We've logged in. Only honor the browser-specified URL if it's not
        # one of the pre-login ones.
        unless segment[0..8] is "dashboard"
          segment = "dashboard"
      else
        segment = "dashboard"
      $rootScope.redirectToSegment segment
    else
      $rootScope.redirectToSegment "login"

  # Page-handling.

  # Convenient way to show a page/segment

  $rootScope.redirectToSegment = (segment) ->
    url = $rootScope.pathForSegment segment
    if url?
      #console.log "Redirecting to #{url}"
      $location.path(url)
    else
      console.log "(BUG) No URL for segment #{segment}"

  $rootScope.segmentIsActive = (segment) ->
    ($routeSegment.name is segment) or ($routeSegment.startsWith("#{segment}."))

  $rootScope.pathForSegment = window.pathForSegment
  $rootScope.hrefForSegment = window.hrefForSegment

  $rootScope.loggedIn = ->
    $rootScope.loggedInUser?

  $rootScope.logout = () ->
    # NOTE: See https://groups.google.com/forum/#!msg/angular/bsTbZ86WAY4/gdpKwc4f7ToJ
    #
    # Specifically, see Majid Burney's response: "They've only disallowed
    # accessing DOM nodes in expressions, not in directives. Your code is only
    # broken because of Coffeescript's bad habit of automatically returning the
    # last value in a function's scope. Angular detects that the function has
    # returned a DOM node and throws an exception to keep you safe. Add an
    # explicit "return" to the end of each of those functions and they should
    # work fine."
    #
    # So, this means the confirm call can't be the last thing in the
    # function.

    if $rootScope.loggedIn()
      confirm.simple "Really log out?", (confirmed) ->
        if confirmed
          always = () ->
            $rootScope.loggedInUser = null

          onSuccess = (response) ->
            always()

          onFailure = (response) ->
            console.log "WARNING: Server logout error. #{response.status}"
            always()

          url = $("#config").data("logout-url")
          data =
            "username": $rootScope.loggedInUser.username
          $http.post(url, data).then onSuccess, onFailure

    return

  # On initial load or reload, we need to determine whether the user is
  # still logged in, since a reload clears everything in the browser.

  redirectIfLoggedOut = ->
    unless $rootScope.loggedIn()
      $rootScope.redirectToSegment("login")

  onSuccess = (response) ->
    if response.data.loggedIn
      $rootScope.loggedInUser = response.data.user
    redirectIfLoggedOut()

  onFailure = (response) ->
    redirectIfLoggedOut()

  url = $("#config").data("logged-in-user-url")
  pwgAjax.post(url, {}, onSuccess, onFailure)


# The app itself.
pwguardApp = angular.module('PWGuardApp', requiredModules)
pwguardApp.config configApp
pwguardApp.run initApp

# ---------------------------------------------------------------------------
# Local Angular.js services
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Controllers
# ---------------------------------------------------------------------------

pwguardApp.controller 'MainCtrl', ($scope, $rootScope) ->
  return

pwguardApp.controller 'NavbarCtrl', ($scope, $rootScope) ->
  return

pwguardApp.controller 'LoginCtrl', ($scope, $rootScope) ->
  $scope.email     = null
  $scope.password  = null
  $scope.canSubmit = false

  $scope.$watch 'email', (newValue, oldValue) ->
    checkSubmit()

  $scope.$watch 'password', (newValue, oldValue) ->
    checkSubmit()

  checkSubmit = ->
    $scope.canSubmit = nonEmpty($scope.email) and nonEmpty($scope.password)

  nonEmpty = (s) ->
    s? and s.trim().length > 0

pwguardApp.controller 'HomeCtrl', ($scope, $rootScope) ->
  return
