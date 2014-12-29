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
                   'ngSanitize',
                   'mgcrea.ngStrap',
                   'angularFileUpload',
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
            pwgTimeout,
            pwgAjax,
            pwgFlash,
            pwgCheckUser,
            pwgLogging) ->

  $scope.debugMessages = []
  $scope.debug = (msg) ->
    $scope.debugMessages.push msg

  log = pwgLogging.logger "MainCtrl"

  $scope.dialogConfirmTitle    = null
  $scope.dialogConfirmMessage  = null
  $scope.loggedInUser          = null
  $scope.$routeSegment         = $routeSegment
  $scope.segmentOnLoad         = window.segmentForURL($location.path())
  $scope.initializing          = true
  $scope.flashAfterRouteChange = null

  $scope.isMobile              = window.browserIsMobile

  pwgFlash.init() # initialize the flash service

  pwgAjax.on401 ->
    $scope.loggedInUser = null
    $scope.redirectToSegment "login"
    $scope.flashAfterRouteChange = "Session timeout. Please log in again."

  $scope.templateURL = (path) ->
    window.angularTemplateURL(path)

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

  userPromise = pwgCheckUser.checkUser()
  $scope.initializing = false

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

pwguardApp.controller 'MainCtrl', ['$scope',
                                   '$routeSegment',
                                   '$location',
                                   'pwgTimeout',
                                   'pwgAjax',
                                   'pwgFlash',
                                   'pwgCheckUser',
                                   'pwgLogging',
                                   MainCtrl]

# ---------------------------------------------------------------------------
# Navigation bar controller
# ---------------------------------------------------------------------------

NavbarCtrl = ($scope, pwgAjax, pwgModal) ->
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
      confirmed = ->
        always = () ->
          $scope.setLoggedInUser null
          $scope.redirectToSegment 'login'

        onSuccess = (response) ->
          always()

        onFailure = (response) ->
          console.log "WARNING: Server logout error. #{response.status}"
          always()

        url = routes.controllers.SessionController.logout().url

        pwgAjax.post(url, {}, onSuccess, onFailure)

      rejected = (reason) ->
        return

      pwgModal.confirm("Really log out?", "Confirm log out").then(confirmed, rejected)

pwguardApp.controller 'NavbarCtrl', ['$scope', 'pwgAjax', 'pwgModal', NavbarCtrl]

# ---------------------------------------------------------------------------
# Login controller
# ---------------------------------------------------------------------------

LoginCtrl = ($scope, pwgAjax, pwgFlash) ->
  $scope.email     = null
  $scope.password  = null

  $scope.login = ->
    handleLogin = (data) ->
      $scope.setLoggedInUser data.user
      $scope.redirectToSegment 'search'

    handleFailure = (data) ->
      # Nothing to do.
      return

    url = routes.controllers.SessionController.login().url
    data =
      email: $scope.email
      password: $scope.password

    pwgAjax.post url, data, handleLogin, handleFailure

  $scope.clear = ->
    $scope.email    = null
    $scope.password = null

  nonEmpty = (s) ->
    s? and s.trim().length > 0

pwguardApp.controller 'LoginCtrl', ['$scope', 'pwgAjax', 'pwgFlash', LoginCtrl]

# ---------------------------------------------------------------------------
# Search controller
# ---------------------------------------------------------------------------

SearchCtrl = ($scope) ->
  return

pwguardApp.controller 'SearchCtrl', ['$scope', SearchCtrl]

InnerSearchCtrl = ($scope, pwgAjax, pwgFlash, pwgTimeout, pwgModal) ->
  console.log "InnerSearchCtrl: NEW"
  $scope.searchTerm     = null
  $scope.searchResults  = null
  $scope.lastSearch     = null
  $scope.activePanel    = -1 # mobile only

  $scope.URLPattern = /^(ftp|http|https):\/\/[^ "]+$/

  SEARCH_ALL_MARKER = "-*-all-*-"

  originalEntries = {}

  $scope.pluralizeResults = (n) ->
    switch n
      when 0 then "No results"
      when 1 then "One result"
      else "#{n} results"

  clearResults = ->
    originalEntries = {}
    $scope.searchResults = null

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
      searchTerm: $scope.searchTerm

    url = routes.controllers.PasswordEntryController.searchPasswordEntries().url
    pwgAjax.post url, params, onSuccess, onFailure

  $scope.showAll = ->
    $scope.newPasswordEntry = null
    onSuccess = (data) ->
      $scope.lastSearch = SEARCH_ALL_MARKER
      $scope.searchResults = adjustResults data.results

    onFailure = (response) ->
      pwgFlash.error "Server error. We're looking into it."

    $scope.searchTerm = null
    url = routes.controllers.PasswordEntryController.all().url
    pwgAjax.get url, onSuccess, onFailure

  $scope.sortColumn = "name"
  $scope.reverse = false
  $scope.sortBy = (column) ->
    if column is $scope.sortColumn
      $scope.reverse = !$scope.reverse
    else
      $scope.sortColumn = column
      $scope.reverse = false

  saveEntry = (pw) ->
    url = routes.controllers.PasswordEntryController.save(pw.id).url
    data =
      name:        pw.name
      description: pw.description
      password:    pw.plaintextPassword
      notes:       pw.notes
      url:         pw.url

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
    pwgModal.confirm("Really delete #{pw.name}", "Confirm deletion").then ->
      url = routes.controllers.PasswordEntryController.delete(pw.id).url
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
      url = routes.controllers.PasswordEntryController.create().url

      onSuccess = ->
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
      url:            ""
      notes:          ""
      cancel: ->
        $scope.newPasswordEntry = null
        reissueLastSearch()
      save: ->
        createNew this
    $scope.searchResults = null

  adjustResults = (results) ->
    originalEntries = {}
    results.map (pw) ->
      pw.showPassword     = false
      pw.editing          = false
      pw.notesPreview     = ellipsize pw.notes
      pw.previewAvailable = pw.notes isnt pw.notesPreview
      pw.showPreview      = pw.previewAvailable
      pw.passwordVisible  = false
      pw.toggleVisibility = ->
        pw.passwordVisible = !pw.passwordVisible

      originalEntries[pw.id] = pw

      pw.edit             = -> this.editing = true
      pw.cancel           = -> cancelEdit this
      pw.save             = -> saveEntry this
      pw.delete           = -> deleteEntry this
      pw


pwguardApp.controller 'InnerSearchCtrl', ['$scope',
                                          'pwgAjax',
                                          'pwgFlash',
                                          'pwgTimeout',
                                          'pwgModal',
                                          InnerSearchCtrl]

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

    url = routes.controllers.UserController.save($scope.loggedInUser.id).url

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
# Import/Export controllers
# ---------------------------------------------------------------------------

ImportExportCtrl = ($scope,
                    $timeout,
                    FileUploader,
                    pwgAjax,
                    pwgFlash,
                    $rootScope,
                    $location) ->

  $scope.downloading = false
  $scope.state = 'new'

  #######################
  # Export              #
  #######################

  $scope.exportFilename = "export.csv"
  $scope.exportURL = ->
    routes.controllers.ImportExportController.exportData($scope.exportFilename).url

  $scope.startDownload = ->
    $scope.downloading = true
    hide = ->
      $scope.downloading = false
    $timeout hide, 3000

  #######################
  # Import              #
  #######################

  # ------------------- #
  # when state == 'new' #
  # ------------------- #

  uploader = new FileUploader()

  fileNamePattern = /\.csv$/
  validFilename = (f) ->
    (f.type is "text/csv") or (fileNamePattern.exec(f.name)?)

  # Make sure the CSV filter is first. The queue limit is also implemented
  # as a filter, and we want it to fire *after* the queue limit filter.
  # Otherwise, the attempt to add an invalid item when the queue has one element
  # will inadvertently clear the queue.
  uploader.filters.unshift {name: 'CSV', fn: validFilename}
  uploader.queueLimit = 1
  uploader.removeAfterUpload = true
  uploader.url = routes.controllers.ImportExportController.importDataUpload().url
  uploader.onWhenAddingFileFailed = (item, filter, options) ->
    if filter.name is "queueLimit"
      # We're replacing an existing file.
      uploader.clearQueue()
      uploader.addToQueue([item])

  $scope.fileSelected = ->
    uploader.getNotUploadedItems().length > 0

  $scope.progress = 0
  uploader.onProgressAll = (prog) ->
    $scope.progress = prog

  uploader.onCompleteAll = ->
    uploader.clearQueue()

  uploader.onAfterAddingFile = ->
    $scope.progress = 0

  uploader.onSuccessItem = (item, response, status, headers) ->
    # The response is JSON.
    pwgAjax.checkResponse response, status, (data) ->
      prepareMappingData data
      $scope.state = 'mapping'

  uploader.onErrorItem = (item, response, status, headers) ->
    pwgAjax.checkResponse response, status

  $scope.uploader = uploader

  # ------------------------ #
  # when state == 'mapping'  #
  # ------------------------ #

  checkForMatch = ->
    i = $scope.headers.filter (h) -> h.selected
    selectedHeader = i[0]
    if selectedHeader?
      i = $scope.fields.filter (f) -> f.selected
      selectedField = i[0]

      if selectedField?
        selectedHeader.matchedTo = selectedField
        selectedField.matchedTo = selectedHeader
        selectedHeader.selected = false
        selectedField.selected = false

  toggleSelection = (item, list) ->
    v = ! item.selected
    for other in list
      other.selected = false
    item.selected = v
    checkForMatch()

  unmatch = (item) ->
    item.matchedTo?.matchedTo = null
    item.matchedTo = null

  prepareMappingData = (data) ->
    $scope.headers = data.headers.map (h) ->
      obj =
        name:      h
        matchedTo: null
        selected:  false

      obj.select = ->
        toggleSelection obj, $scope.headers

      obj.unmatch = ->
        unmatch obj

      obj

    $scope.fields = data.fields.map (f) ->
      obj =
        name:      f.name
        matchedTo: null
        selected:  false
        required:  f.required

      obj.select = ->
        toggleSelection obj, $scope.fields

      obj.unmatch = ->
        unmatch obj

      obj

    # Pre-match on name.
    fields = {}
    for f in $scope.fields
      fields[f.name] = f
    for h in $scope.headers
      matchedField = fields[h.name]
      if matchedField?
        matchedField.matchedTo = h
        h.matchedTo = matchedField

  $scope.availableItem = (item) ->
    ! (item.matchedTo?)

  $scope.matchedItem = (item) ->
    item.matchedTo?

  $scope.allMatched = ->
    totalRequired = $scope.fields.filter (f) -> f.required
    console.log "totalRequired=#{totalRequired.length}"
    matchedRequired = $scope.headers.filter (h) -> h.matchedTo?.required
    console.log "matchedRequired=#{matchedRequired.length}"
    totalRequired.length is matchedRequired.length

  $scope.completeImport = ->
    data =
      mappings: {}

    for k in $scope.fields
      if k.matchedTo?
        data.mappings[k.name] = k.matchedTo.name

    url = routes.controllers.ImportExportController.completeImport().url
    pwgAjax.post url, data, (response) ->
      $scope.state = 'complete'
      handleCompletion(response.total)

  # ------------------------ #
  # when state == 'complete' #
  # ------------------------ #

  $scope.completionCount
  handleCompletion = (total) ->
    $scope.completionCount = switch total
      when 0 then "no new entries"
      when 1 then "1 new entry"
      else "#{total} new entries"


  $scope.reset = ->
    $scope.state = 'new'
    $scope.progress = 0
    pwgFlash.clearAll()

pwguardApp.controller 'ImportExportCtrl', ['$scope',
                                           '$timeout',
                                           'FileUploader',
                                           'pwgAjax',
                                           'pwgFlash',
                                           '$rootScope',
                                           '$location',
                                           ImportExportCtrl]

# ---------------------------------------------------------------------------
# Admin users controller
# ---------------------------------------------------------------------------

AdminUsersCtrl = ($scope, pwgAjax, pwgFlash, pwgModal) ->
  $scope.users = null
  $scope.addingUser = null

  originalUsers = {}

  saveUser = (u) ->
    u.passwordsMatch = passwordsOkay u.password1, u.password2
    if u.passwordsMatch
      url = routes.controllers.UserController.save(u.id).url

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
      pwgModal.confirm("Really delete #{u.email}?", "Confirm deletion").then ->
        url = routes.controllers.UserController.delete(u.id).url
        pwgAjax.delete url, ->
          loadUsers()

  checkSave = (u) ->
    msg = if normalizeValue(u.email) == ""
      "Missing email address."
    else if normalizeValue(u.password1) == ""
      "Missing password."
    else if (not passwordsOkay(u.password1, u.password2))
      "Passwords don't match."
    else
      null

  createUser = (u) ->
    msg = checkSave u
    if msg?
      pwgFlash.error msg
    else
      url = routes.controllers.UserController.create().url

      onSuccess = ->
        loadUsers()
        $scope.addingUser = null

      onFailure = (data) ->
        pwgFlash.error data.error.message

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
      cancel:         -> $scope.addingUser = null
      save:           -> createUser this
      clear:          ->
        $scope.addingUser.email     = null
        $scope.addingUser.password1 = null
        $scope.addingUser.password2 = null
        $scope.addingUser.firstName = null
        $scope.addingUser.lastName  = null
        $scope.addingUser.active    = true
        $scope.addingUser.admin     = false

  canSave = (u) ->
    checkSave(u) isnt null

  loadUsers = ->
    originalUsers = {}
    $scope.users = null
    url = routes.controllers.UserController.getAll().url
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
        u2.canSave   = -> canSave this

        u2.passwordsMatch = true
        u2

  $scope.$watch 'segmentIsActive("admin-users")', (visible) ->
    loadUsers() if visible

pwguardApp.controller 'AdminUsersCtrl', ['$scope',
                                         'pwgAjax',
                                         'pwgFlash',
                                         'pwgModal',
                                         AdminUsersCtrl]
