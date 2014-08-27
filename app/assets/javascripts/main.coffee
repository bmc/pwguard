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
                   'angular-accordion',
                   'ngCookies',
                   'ui.utils',
                   'tableSort',
                   'Mac',
                   'pwguard-services',
                   'pwguard-filters',
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

# The app itself.
pwguardApp = angular.module('PWGuardApp', requiredModules)
pwguardApp.config ['$routeSegmentProvider',
                   '$routeProvider',
                   '$locationProvider',
                   configApp]

###############################################################################
# Local functions
###############################################################################

# Instantiating the module this way, rather than via "ng-app", provides
# better browser console errors.
###
try
  angular.bootstrap document, ['PWGuardApp', requiredModules]
catch e
  console.error e.stack or e.message or e
  throw e
###

fieldsMatch = (v1, v2) ->
  normalizeValue(v1) is normalizeValue(v2)

normalizeValue = (v) ->
  if v? then v else ""

passwordsOkay = (pw1, pw2) ->
  normalizeValue(pw1) is normalizeValue(pw2)

ellipsize = (input, max=30) ->
  if input?
    m = parseInt max
    if isNaN(max)
      console.log "Bad max value: #{max}"
      m = 30

    trimmed = input[0..m]
    if trimmed is input then input else "#{trimmed}..."
  else
    null

###############################################################################
# Controllers
###############################################################################

# ---------------------------------------------------------------------------
# Main controller
# ---------------------------------------------------------------------------


MainCtrl = ($scope,
            $routeSegment,
            $location,
            macModal,
            pwgTimeout,
            pwgAjax,
            pwgFlash,
            pwgCheckUser,
            pwgGetBrowserInfo,
            pwgLogging,
            $q) ->

  log = pwgLogging.logger "MainCtrl"

  $scope.dialogConfirmTitle    = null
  $scope.dialogConfirmMessage  = null
  $scope.loggedInUser          = null
  $scope.$routeSegment         = $routeSegment
  $scope.segmentOnLoad         = window.segmentForURL($location.path())
  $scope.initializing          = true
  $scope.flashAfterRouteChange = null

  pwgFlash.init() # initialize the flash service

  pwgAjax.on401 ->
    $scope.loggedInUser = null
    $scope.redirectToSegment "login"
    $scope.flashAfterRouteChange = "Session timeout. Please log in again."

  $scope.$on '$routeChangeSuccess', ->
    # Clear flash messages on route change.
    pwgFlash.clear 'all'
    if $scope.flashAfterRouteChange?
      pwgFlash.info $scope.flashAfterRouteChange
      $scope.flashAfterRouteChange = null

  $scope.$on '$locationChangeStart', (e) ->
    # Skip, while initializing. (Doing this during initialization screws
    # things up, causing multiple redirects that play games with Angular.)
    unless $scope.initializing
      segment = window.segmentForURL $location.path()
      useSegment = validateLocationChange segment
      log.debug "segment=#{segment}, useSegment=#{useSegment}"
      if useSegment isnt segment
        e.preventDefault()
        $scope.redirectToSegment useSegment

  # Page-handling.

  # Convenient way to show a page/segment

  $scope.redirectToSegment = (segment) ->
    url = $scope.pathForSegment segment
    if url?
      log.debug "Redirecting to #{url}"
      log.trace (new Error("Debug stack trace").stack)
      $location.path(url)
    else
      console.log "(BUG) No URL for segment #{segment}"

  $scope.segmentIsActive = (segment) ->
    ($routeSegment.name is segment) or ($routeSegment.startsWith("#{segment}."))

  $scope.pathForSegment = window.pathForSegment
  $scope.hrefForSegment = window.hrefForSegment

  $scope.loggedIn = ->
    $scope.loggedInUser?

  # NOTE: It's important to o l
  $scope.setLoggedInUser = (user) ->
    if user? and user.email?
      $scope.loggedInUser = user
    else
      $scope.loggedInUser = null

  # On initial load or reload, we need to determine whether the user is
  # still logged in, since a reload clears everything in the browser.

  validateLocationChange = (segment) ->
    useSegment = null
    if $scope.loggedInUser?
      # Ensure that the segment is valid for a logged in user.
      useSegment = 'search' # default
      if segment?
        if window.isPostLoginSegment(segment)
          if $scope.loggedInUser.admin
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

  userPromise        = pwgCheckUser.checkUser()
  browserInfoPromise = pwgGetBrowserInfo.getBrowserInfo()
  combinedPromise    = $q.all [userPromise, browserInfoPromise]

  combinedPromise.then ->
    $scope.initializing = false

    # Check each of the completed promises.

    userInfoSuccess = (response) ->
      if response.loggedIn
        $scope.setLoggedInUser response.user
      else
        $scope.setLoggedInUser null

      useSegment = validateLocationChange $scope.segmentOnLoad
      $scope.redirectToSegment useSegment
      $scope.segmentOnLoad = false

    userInfoFailure = (response) ->
      $scope.setLoggedInUser null
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
                                   'modal',
                                   'pwgTimeout',
                                   'pwgAjax',
                                   'pwgFlash',
                                   'pwgCheckUser',
                                   'pwgGetBrowserInfo'
                                   'pwgLogging',
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
          $scope.setLoggedInUser null
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

  ### DEBUG
  $scope.email = "admin@example.com"; $scope.password = "admin"
  ###

  $scope.$watch 'email', (newValue, oldValue) ->
    checkSubmit()

  $scope.$watch 'password', (newValue, oldValue) ->
    checkSubmit()

  $scope.login = ->
    if $scope.canSubmit
      handleLogin = (data) ->
        $scope.setLoggedInUser data.user
        $scope.redirectToSegment 'search'

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

SearchCtrl = ($scope, pwgAjax, pwgFlash, pwgTimeout) ->
  $scope.searchTerm        = null
  $scope.searchResults     = null
  $scope.searchDescription = true
  $scope.matchFullWord     = false
  $scope.lastSearch        = null

  SEARCH_ALL_MARKER = "-*-all-*-"

  originalEntries = {}

  clearResults = ->
    originalEntries = {}
    $scope.searchResults = null

  for v in ['searchDescription', 'matchFullWord']
    $scope.$watch v, ->
      searchOptionChanged()

  keyboardTimeout = null
  $scope.searchTermChanged = ->
    if validSearchTerm()
      # Allow time for user to finish typing.
      pwgTimeout.cancel keyboardTimeout if keyboardTimeout?
      keyboardTimeout = pwgTimeout.timeout 250, doSearch
    else
      clearResults()

  $scope.mobileSelect = (i) ->
    $("#result-#{i}").select()

  searchOptionChanged = ->
    if validSearchTerm()
      doSearch()
    else
      clearResults()

  validSearchTerm = ->
    trimmed = if $scope.searchTerm? then $scope.searchTerm.trim() else ""
    trimmed.length >= 2

  doSearch = ->
    originalEntries = {}
    $scope.newPasswordEntry = null

    onSuccess = (data) ->
      $scope.lastSearch = $scope.searchTerm
      $scope.searchResults = adjustResults data.results

    onFailure = (response) ->
      pwgFlash.error "Server error issuing the search. We're looking into it."

    params =
      searchTerm:         $scope.searchTerm
      includeDescription: $scope.searchDescription
      wordMatch:          $scope.matchFullWord

    url = $("#config").data('search-url')
    pwgAjax.post url, params, onSuccess, onFailure

  $scope.showAll = ->
    $scope.newPasswordEntry = null
    onSuccess = (data) ->
      $scope.lastSearch = SEARCH_ALL_MARKER
      $scope.searchResults = adjustResults data.results

    onFailure = (response) ->
      pwgFlash.error "Server error. We're looking into it."

    $scope.searchTerm = null
    url = $("#config").data("all-pw-url")
    pwgAjax.get url, onSuccess, onFailure

  saveEntry = (pw) ->
    url = $("#config").data("save-pwentry-url").replace("0", pw.id)
    data =
      name:        pw.name
      description: pw.description
      password:    pw.plaintextPassword
      notes:       pw.notes

    onSuccess = ->
      pw.editing = false
      reissueLastSearch()

    pwgAjax.post url, data, onSuccess

  reissueLastSearch = ->
    if $scope.lastSearch?
      if $scope.lastSearch is SEARCH_ALL_MARKER
        $scope.showAll()
      else
        $scope.searchTerm = $scope.lastSearch
        doSearch()

  deleteEntry = (pw) ->
    $scope.confirm("Really delete #{pw.name}?", "Confirm deletion").then (result) ->
      url = $("#config").data("delete-pwentry-url").replace("0", pw.id)
      pwgAjax.delete url, ->
        reissueLastSearch()

  cancelEdit = (pw) ->
    _.extend pw, originalEntries[pw.id]
    pw.editing = false
    reissueLastSearch()

  createNew = (pw) ->
    if normalizeValue(pw.name) == ""
      pwgFlash.error "Missing name."
    else
      url = $("#config").data("create-pwentry-url")

      onSuccess = ->
        $scope.showAll()
        $scope.newPasswordEntry = null
        reissueLastSearch()

      onFailure = (data) ->
        pwgFlash.error "Save failed. #{data.error?.message}"

      pwgAjax.post url, $scope.newPasswordEntry, onSuccess, onFailure

  $scope.editingAny = ->
    if $scope.searchResults?
      first = _.find $scope.searchResults, (pw) -> pw.editing
      first?
    else
      false

  $scope.editNewEntry = ->
    $scope.newPasswordEntry =
      id:             null
      name:           ""
      loginID:        ""
      password:       ""
      description:    ""
      notes:          ""
      cancel: ->
        $scope.newPasswordEntry = null
        reissueLastSearch()
      save: ->
        createNew this
    $scope.searchResults = null

  adjustResults = (results) ->
    originalEntries = {}
    for pw in results
      pw.showPassword     = false
      pw.editing          = false
      pw.notesPreview     = ellipsize pw.notes
      pw.previewAvailable = pw.notes isnt pw.notesPreview
      pw.showPreview      = pw.previewAvailable

      originalEntries[pw.id] = pw

      pw.edit             = -> this.editing = true
      pw.cancel           = -> cancelEdit this
      pw.save             = -> saveEntry this
      pw.delete           = -> deleteEntry this
      pw

pwguardApp.controller 'SearchCtrl', ['$scope',
                                     'pwgAjax',
                                     'pwgFlash',
                                     'pwgTimeout',
                                     SearchCtrl]

# ---------------------------------------------------------------------------
# Profile controller
# ---------------------------------------------------------------------------

ProfileCtrl = ($scope, pwgLogging, pwgAjax) ->

  log = pwgLogging.logger "ProfileCtrl"

  $scope.email          = $scope.loggedInUser?.email
  $scope.firstName      = $scope.loggedInUser?.firstName
  $scope.lastName       = $scope.loggedInUser?.lastName
  $scope.password1      = null
  $scope.password2      = null

  $scope.error =
    password1: null
    password2: null
    firstName: null
    lastName:  null

  $scope.dirty = ->

    dirty = (not fieldsMatch($scope.email, $scope.loggedInUser?.email)) or
            (not fieldsMatch($scope.firstName, $scope.loggedInUser?.firstName)) or
            (not fieldsMatch($scope.lastName, $scope.loggedInUser?.lastName)) or
            (normalizeValue($scope.password1) isnt "") or
            (normalizeValue($scope.password2) isnt "")
    dirty

  $scope.canSubmit = ->
    error = checkErrors()
    (not error) and $scope.dirty()

  $scope.fieldInError = (field) ->
    checkErrors()
    $scope.error[field]?

  $scope.save = ->
    data =
      firstName: $scope.firstName
      lastName:  $scope.lastName
      password1: $scope.password1
      password2: $scope.password2

    url = $("#config").data("save-user-url").replace("0", $scope.loggedInUser.id)

    pwgAjax.post url, data, (response) ->
      log.debug "Save complete."
      $scope.setLoggedInUser response

  checkErrors = ->
    for k of $scope.error
      $scope.error[k] = null

    if not passwordsOkay($scope.password1, $scope.password2)
      $scope.error.password1 = "Passwords don't match."

    errors = ($scope.error[k] for k of $scope.error).filter (e) -> e
    errors.length > 0

pwguardApp.controller 'ProfileCtrl', ['$scope',
                                      'pwgLogging',
                                      'pwgAjax',
                                      ProfileCtrl]

# ---------------------------------------------------------------------------
# Admin users controller
# ---------------------------------------------------------------------------

AdminUsersCtrl = ($scope, pwgAjax, pwgFlash) ->
  $scope.users = null
  $scope.addingUser = null

  originalUsers = {}

  saveUser = (u) ->
    u.passwordsMatch = passwordsOkay u.password1, u.password2
    if u.passwordsMatch
      url = $("#config").data('save-user-url').replace("0", u.id)

      onFailure = ->
        pwgFlash.error "Save failed."

      onSuccess = ->
        originalUsers[u.email] = _.omit 'save', 'cancel', 'edit', 'editing'
        u.editing = false
        loadUsers()

      pwgAjax.post url, u, onSuccess, onFailure
    else
      pwgFlash.error "Passwords don't match."

  cancelEdit = (u) ->
    _.extend u, originalUsers[u.email]
    u.editing = false

  deleteUser = (u) ->
    if u.id is $scope.loggedInUser.id
      pwgFlash.error "You can't delete yourself!"

    else
      $scope.confirm("Really delete #{u.email}?",
                     "Confirm deletion").then (result) ->
        url = $("#config").data("delete-user-url").replace("0", u.id)
        pwgAjax.delete url, ->
          loadUsers()

  createUser = (u) ->
    if normalizeValue(u.email) == ""
      pwgFlash.error "Missing email address."
    else if normalizeValue(u.password1) == ""
      pwgFlash.error "Missing password."
    else if (not passwordsOkay(u.password1, u.password2))
      pwgFlash.error "Passwords don't match."
    else
      url = $("#config").data("create-user-url")

      onSuccess = ->
        loadUsers()
        $scope.addingUser = null

      onFailure = ->
        pwgFlash.error "Save failed."

      pwgAjax.post url, $scope.addingUser, onSuccess, onFailure

  $scope.editingAny = ->
    if $scope.users?
      first = _.find $scope.users, (u) -> u.editing
      first?
    else
      false

  $scope.editNewUser = ->
    $scope.addingUser =
      id:             null
      email:          ""
      password1:      ""
      password2:      ""
      firstName:      ""
      lastName:       ""
      admin:          false
      active:         true
      editing:        true
      isNew:          true
      passwordsMatch: true
      cancel: ->
        $scope.addingUser = null
      save: ->
        createUser this

  loadUsers = ->
    originalUsers = {}
    $scope.users = null
    url = $("#config").data("all-users-url")
    pwgAjax.get url, (result) ->
      $scope.users = for u in result.users
        if u.id is $scope.loggedInUser?.id
          $scope.setLoggedInUser u

        u2 = _.clone u
        u2.editing   = false
        u2.password1 = ""
        u2.password2 = ""
        u2.isNew     = false
        originalUsers[u.email] = _.clone u

        u2.edit      = -> this.editing = true
        u2.save      = -> saveUser this
        u2.cancel    = -> cancelEdit this
        u2.delete    = -> deleteUser this

        u2.passwordsMatch = true

        u2

  $scope.$watch 'segmentIsActive("admin-users")', (visible) ->
    loadUsers() if visible

pwguardApp.controller 'AdminUsersCtrl', ['$scope',
                                         'pwgAjax',
                                         'pwgFlash',
                                         AdminUsersCtrl]
