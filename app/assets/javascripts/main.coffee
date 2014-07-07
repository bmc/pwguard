###############################################################################
# Some non-Angular stuff.
###############################################################################

###############################################################################
# Angular JS stuff
###############################################################################

requiredModules = ['ngRoute',
                   'ngAnimate',
                   'route-segment',
                   'view-segment',
                   'ngCookies',
                   'ui.utils',
                   'tableSort',
                   'Mac',
                   'pwguard-services',
                   'pwguard-directives']

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


# The app itself.
pwguardApp = angular.module('PWGuardApp', requiredModules)
pwguardApp.config ['$routeSegmentProvider',
                   '$routeProvider',
                   '$locationProvider',
                   configApp]

# Instantiating the module this way, rather than via "ng-app", provides
# better browser console errors.
###
try
  angular.bootstrap document, ['PWGuardApp', requiredModules]
catch e
  console.error e.stack or e.message or e
  throw e
###

###############################################################################
# Controllers
###############################################################################

# ---------------------------------------------------------------------------
# Main controller
# ---------------------------------------------------------------------------


MainCtrl = ($scope,
            $routeSegment,
            $location,
            pwgTimeout,
            pwgAjax,
            pwgFlash,
            pwgCheckUser,
            pwgGetBrowserInfo,
            macModal,
            $q) ->

  $scope.dialogConfirmTitle   = null
  $scope.dialogConfirmMessage = null

  $scope.loggedInUser  = null
  $scope.$routeSegment = $routeSegment
  $scope.segmentOnLoad = window.segmentForURL($location.path())
  $scope.initializing  = true

  pwgFlash.init() # initialize the flash service

  $scope.$on '$routeChangeSuccess', ->
    # Clear flash messages on route change.
    pwgFlash.clear 'all'

  # Page-handling.

  # Convenient way to show a page/segment

  $scope.redirectToSegment = (segment) ->
    url = $scope.pathForSegment segment
    if url?
      #console.log "Redirecting to #{url}"
      $location.path(url)
    else
      console.log "(BUG) No URL for segment #{segment}"

  $scope.segmentIsActive = (segment) ->
    ($routeSegment.name is segment) or ($routeSegment.startsWith("#{segment}."))

  $scope.pathForSegment = window.pathForSegment
  $scope.hrefForSegment = window.hrefForSegment

  $scope.loggedIn = ->
    $scope.loggedInUser?

  $scope.saveLoggedInUser = (user) ->
    $scope.loggedInUser =
      email:       user.email
      isAdmin:     user.admin
      displayName: user.displayName
      firstName:   user.firstName
      lastName:    user.lastName
      isMobile:    user.isMobile

  # On initial load or reload, we need to determine whether the user is
  # still logged in, since a reload clears everything in the browser.

  $scope.redirectIfNotLoggedIn

  validateLocationChange = (segment) ->
    useSegment = null
    if $scope.loggedInUser?
      # Ensure that the segment is valid for a logged in user.
      useSegment = 'search' # default
      if segment?
        if window.isPostLoginSegment(segment)
          if $scope.loggedInUser.isAdmin
            # Admins can go anywhere.
            useSegment = segment
          else if (not window.isAdminOnlySegment(segment))
            # Non-admins can go to non-admin segments.
            useSegment = segment

    else
      # Ensure that the segment is valid for a non-logged in user.
      if segment? and window.isPreLoginSegment(segment)
        useSegment = segment
      else
        useSegment = 'login'

    useSegment

  $scope.$on '$locationChangeStart', (e) ->
    segment = window.segmentForURL $location.path()
    useSegment = validateLocationChange segment
    if useSegment isnt segment
      e.preventDefault()
      $scope.redirectToSegment useSegment

  userPromise        = pwgCheckUser.checkUser()
  browserInfoPromise = pwgGetBrowserInfo.getBrowserInfo()
  combinedPromise    = $q.all [userPromise, browserInfoPromise]

  combinedPromise.then ->
    $scope.initializing = false

    # Check each of the completed promises.

    userInfoSuccess = (response) ->
      if response.loggedIn
        $scope.loggedInUser = response.user
      else
        $scope.loggedInUser = null

      useSegment = validateLocationChange $scope.segmentOnLoad
      $scope.redirectToSegment useSegment

    userInfoFailure = (response) ->
      $scope.loggedInUser = null
      $scope.redirectToSegment "login"

    userPromise.then userInfoSuccess, userInfoFailure

    browserInfoSuccess = (userAgentInfo) ->
      $scope.isMobile = userAgentInfo.isMobile

    browserInfoFailure = (response) ->
      $scope.isMobile = false

    browserInfoPromise.then browserInfoSuccess

  # Modal handler
  modalDeferred = null

  $scope.ok = ->
    macModal.hide ->
      modalDeferred.resolve()
      modalDeferred = null

  $scope.cancel = ->
    macModal.hide ->
      modalDeferred.reject()
      modalDeferred = null

  # Shows an appropriate confirmation dialog, depending on whether the user
  # is mobile or not. Returns a promise (via $q) that resolves on confirmation
  # and rejects on cancel.
  #
  # Parameters:
  #   message - the confirmation message
  #   title   - optional title for the dialog, if supported
  $scope.confirm = (message, title) ->
    modalDeferred = $q.defer()

    if $scope.isMobile
      if confirm message
        modalDeferred.resolve()
      else
        modalDeferred.reject()

    else
      $scope.dialogConfirmTitle   = title
      $scope.dialogConfirmMessage = message

      macModal.show 'confirm-dialog'

    modalDeferred.promise

pwguardApp.controller 'MainCtrl', ['$scope',
                                   '$routeSegment',
                                   '$location',
                                   'pwgTimeout',
                                   'pwgAjax',
                                   'pwgFlash',
                                   'pwgCheckUser',
                                   'pwgGetBrowserInfo'
                                   'modal',
                                   '$q',
                                   MainCtrl]

# ---------------------------------------------------------------------------
# Navigation bar controller
# ---------------------------------------------------------------------------

NavbarCtrl = ($scope, pwgAjax) ->
  $scope.logout = () ->
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

    if $scope.loggedIn()
      $scope.confirm("Really log out?", "Confirm log out").then (result) ->
        always = () ->
          $scope.loggedInUser = null
          $scope.redirectToSegment 'login'

        onSuccess = (response) ->
          always()

        onFailure = (response) ->
          console.log "WARNING: Server logout error. #{response.status}"
          always()

        url = $("#config").data("logout-url")

        pwgAjax.post(url, {}, onSuccess, onFailure)

pwguardApp.controller 'NavbarCtrl', ['$scope', 'pwgAjax', NavbarCtrl]

# ---------------------------------------------------------------------------
# Login controller
# ---------------------------------------------------------------------------

LoginCtrl = ($scope, pwgAjax, pwgFlash) ->
  $scope.email     = null
  $scope.password  = null
  $scope.canSubmit = false

  #### DEBUG
  $scope.email = "admin@example.com"; $scope.password = "admin"
  #### END DEBUG

  $scope.$watch 'email', (newValue, oldValue) ->
    checkSubmit()

  $scope.$watch 'password', (newValue, oldValue) ->
    checkSubmit()

  $scope.login = ->
    if $scope.canSubmit
      handleLogin = (data) ->
        $scope.saveLoggedInUser(data.user)
        $scope.redirectToSegment('search')

      handleFailure = (data) ->
        # Nothing to do.
        return

      url = $("#config").data("login-url")
      data =
        email: $scope.email
        password: $scope.password

      pwgAjax.post url, data, handleLogin, handleFailure

  $scope.clear = ->
    $scope.email    = null
    $scope.password = null

  checkSubmit = ->
    $scope.canSubmit = nonEmpty($scope.email) and nonEmpty($scope.password)


  nonEmpty = (s) ->
    s? and s.trim().length > 0

pwguardApp.controller 'LoginCtrl', ['$scope', 'pwgAjax', 'pwgFlash', LoginCtrl]

# ---------------------------------------------------------------------------
# Search controller
# ---------------------------------------------------------------------------

SearchCtrl = ($scope, pwgAjax) ->
  $scope.searchTerm    = null
  $scope.searchResults = null

  $scope.searchTermChanged = ->
    trimmed = if $scope.searchTerm? then $scope.searchTerm.trim() else ""
    len     = trimmed.length
    if len >= 2
      doSearch()
    else
      $scope.searchResults = null

  doSearch = ->
    url = $("#config").data('search-url')

    onSuccess = (data) ->
      $scope.searchResults = data.results

    params =
      searchTerm:         $scope.searchTerm
      includeDescription: true
      wordMatch:          false

    pwgAjax.post url, params, onSuccess

pwguardApp.controller 'SearchCtrl', ['$scope', 'pwgAjax', SearchCtrl]

# ---------------------------------------------------------------------------
# Profile controller
# ---------------------------------------------------------------------------

ProfileCtrl = ($scope) ->
  return

pwguardApp.controller 'ProfileCtrl', ['$scope', ProfileCtrl]

# ---------------------------------------------------------------------------
# Admin users controller
# ---------------------------------------------------------------------------

AdminUsersCtrl = ($scope) ->
  return

pwguardApp.controller 'AdminUsersCtrl', ['$scope', AdminUsersCtrl]
