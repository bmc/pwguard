###############################################################################
# Some non-Angular stuff.
###############################################################################

###############################################################################
# Angular JS stuff
###############################################################################

requiredModules = ['ngRoute',
                   'ngCookies',
                   'ngSanitize',
                   'mgcrea.ngStrap',
                   'pwguard-services',
                   'pwguard-filters',
                   'pwguard-directives']
# The app itself.
pwguardApp = angular.module('PWGuardApp', requiredModules)

ROUTES            = {}
POST_LOGIN_ROUTES = []
ADMIN_ONLY_ROUTES = []
REVERSE_ROUTES    = {}
DEFAULT_ROUTE     = null

initializeRouting = ($routeProvider) ->
  templateURL   = window.angularTemplateURL

  # Routing table

  ROUTES =
    "/login":
      templateUrl: templateURL("login.html")
      controller:  'LoginCtrl'
      name:        'login'
      admin:       false
      postLogin:   false
    "/search":
      templateUrl: templateURL("search.html")
      controller:  'SearchCtrl'
      name:        'search'
      admin:       false
      postLogin:   true
      defaultURL:  true
    "/profile":
      templateUrl: templateURL("profile.html")
      controller:  'ProfileCtrl'
      name:        'profile'
      admin:       false
      postLogin:   true
    "/admin/users":
      templateUrl: templateURL("admin-users.html")
      controller:  'AdminUsersCtrl'
      name:        'admin-users'
      admin:       true
      postLogin:   true
    "/import-export":
      templateUrl: templateURL("ImportExport.html")
      controller:  'ImportExportCtrl'
      name:        'import-export'
      admin:       false
      postLogin:   true
    "/about":
      templateUrl: templateURL("about.html")
      controller:  'AboutCtrl'
      name:        'about'
      admin:       false
      postLogin:   true

  for url of ROUTES
    r = ROUTES[url]
    REVERSE_ROUTES[r.name] = url

    cfg =
      templateUrl: r.templateUrl
      controller:  r.controller

    $routeProvider.when url, cfg
    if r.defaultURL
      DEFAULT_ROUTE = r.name

    if r.postLogin
      POST_LOGIN_ROUTES.push r.name

    if r.admin
      ADMIN_ONLY_ROUTES.push r.name

  if DEFAULT_ROUTE
    cfg =
      redirectTo: DEFAULT_ROUTE
    $routeProvider.otherwise cfg

checkMissingFeatures = ->
  missing = []
  unless window.FileReader and Modernizr.draganddrop
    missing.push("drag-and-drop")
  unless Modernizr.canvastext
    missing.push("canvas")

  if missing.length > 0
    alert("Your browser lacks the following HTML 5 features:\n\n" +
          missing.join(', ') + "\n\n" +
          "Parts of this application may not behave as designed or may fail " +
          "completely. Please consider using a newer browser.")

config = ($routeProvider) ->
  initializeRouting $routeProvider
  checkMissingFeatures()

pwguardApp.config ['$routeProvider', config]

pwgRoutes = (pwgLogging, pwgError, $location, $route) ->

  log = pwgLogging.logger "pwgRoutes"

  URL_RE = /^.*#(.*)$/

  postLoginRoute = (name) ->
    name in POST_LOGIN_ROUTES

  isAdminOnlyRoute: (name) ->
    name in ADMIN_ONLY_ROUTES

  isPostLoginRoute: (name) ->
    postLoginRoute(name)

  isPreLoginRoute: (name) ->
    not postLoginRoute(name)

  pathForRouteName: (name) ->
    REVERSE_ROUTES[name]

  hrefForRouteName: (name) ->
    "#" + pathForRouteName(name)

  defaultRoute: ->
    DEFAULT_ROUTE

  routeIsActive: (name) ->
    path = REVERSE_ROUTES[name]
    path? and $location.path().endsWith(path)

  routeNameForURL: (url) ->
    url = if url? then url else ""
    m = URL_RE.exec(url)
    strippedURL = if m then m[1] else url
    result = null
    for r of REVERSE_ROUTES
      if REVERSE_ROUTES[r] is strippedURL
        result = r
        break

    result

  redirectToNamedRoute: (name) ->
    url = REVERSE_ROUTES[name]
    if url?
      log.debug "Redirecting to #{url}"
      log.trace (new Error("Debug stack trace").stack)
      $location.path(url)
    else
      pwgError.showStackTrace "(BUG) No URL for route #{name}"


pwguardApp.factory 'pwgRoutes', ['pwgLogging',
                                 'pwgError',
                                 '$location',
                                 '$route',
                                 pwgRoutes]

###############################################################################
# Local functions
###############################################################################


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
            $rootScope,
            $location,
            pwgTimeout,
            pwgAjax,
            pwgFlash,
            pwgCheckUser,
            pwgLogging,
            pwgRoutes,
            angularTemplateURL,
            pwgError) ->

  # Put the template URL in the scope, because it's used inside templates
  # (e.g., within ng-include directives).
  $scope.templateURL = angularTemplateURL
  $scope.version     = window.version;

  $scope.debugMessages = []
  $scope.debug = (msg) ->
    $scope.debugMessages.push msg

  log = pwgLogging.logger "MainCtrl"

  $scope.dialogConfirmTitle    = null
  $scope.dialogConfirmMessage  = null
  $scope.loggedInUser          = null
  $scope.routeOnLoad           = pwgRoutes.routeNameForURL($location.path())
  $scope.initializing          = true
  $scope.flashAfterRouteChange = null

  $scope.isMobile              = window.browserIsMobile

  pwgAjax.on401 ->
    if $scope.loggedInUser
      $scope.loggedInUser = null
      $scope.redirectToNamedRoute "login"
      $scope.flashAfterRouteChange = "Session timeout. Please log in again."
    else
      pwgFlash.error "Login failure."

  $scope.$on '$routeChangeSuccess', ->
    # Clear flash messages on route change.
    pwgFlash.clearAll()
    if $scope.flashAfterRouteChange?
      pwgFlash.info $scope.flashAfterRouteChange
      $scope.flashAfterRouteChange = null

  $scope.routeIsActive  = (name) ->
    pwgRoutes.routeIsActive(name)

  $scope.$on '$locationChangeStart', (e) ->
    # Skip, while initializing. (Doing this during initialization screws
    # things up, causing multiple redirects that play games with Angular.)
    unless $scope.initializing
      routeName = pwgRoutes.routeNameForURL $location.path()
      useRoute = validateLocationChange routeName
      log.debug "routeName=#{routeName}, useRoute=#{useRoute}"
      if useRoute isnt routeName
        $scope.redirectToNamedRoute useRoute

  # Page-handling.

  # Convenient way to show a page/segment

  $scope.redirectToNamedRoute = (name) ->
    url = pwgRoutes.pathForRouteName name
    if url?
      log.debug "Redirecting to #{url}"
      log.trace (new Error("Debug stack trace").stack)
      $location.path(url)
    else
      pwgError.showStackTrace "(BUG) No URL for route #{name}"

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

  validateLocationChange = (routeName) ->
    useRoute = null
    if $scope.loggedInUser?
      # Ensure that the segment is valid for a logged in user.
      useRoute = pwgRoutes.defaultRoute()
      if routeName?
        if pwgRoutes.isPostLoginRoute(routeName)
          if $scope.loggedInUser.admin
            # Admins can go anywhere.
            useRoute = routeName
          else if (not pwgRoutes.isAdminOnlyRoute(segment))
            # Non-admins can go to non-admin segments.
            useRoute = routeName

    else
      # Ensure that the segment is valid for a non-logged in user.
      if routeName? and pwgRoutes.isPreLoginRoute(routeName)
        useRoute = routeName
      else
        useRoute = 'login'

    useRoute

  userPromise = pwgCheckUser.checkUser()

  userInfoSuccess = (response) ->
    $scope.initializing = false
    if response.loggedIn
      $scope.setLoggedInUser response.user
    else
      $scope.setLoggedInUser null

    useRoute = validateLocationChange $scope.routeOnLoad
    pwgRoutes.redirectToNamedRoute useRoute
    $scope.routeOnLoad = null

  userInfoFailure = (response) ->
    $scope.initializing = false
    $scope.setLoggedInUser null
    $scope.redirectToNamedRoute "login"

  userPromise.then userInfoSuccess, userInfoFailure

pwguardApp.controller 'MainCtrl', ['$scope',
                                   '$rootScope',
                                   '$location',
                                   'pwgTimeout',
                                   'pwgAjax',
                                   'pwgFlash',
                                   'pwgCheckUser',
                                   'pwgLogging',
                                   'pwgRoutes',
                                   'angularTemplateURL',
                                   'pwgError',
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
          $scope.redirectToNamedRoute 'login'

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
      $scope.redirectToNamedRoute 'search'

    handleFailure = (data) ->
      console.log data
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

pwguardApp.controller 'SearchCtrl', ['$scope', 'pwgFlash', SearchCtrl]

InnerSearchCtrl = ($scope, pwgAjax, pwgFlash, pwgTimeout, pwgModal, $filter) ->
  $scope.searchTerm     = null
  $scope.searchResults  = null
  $scope.lastSearch     = null
  $scope.activePanel    = -1 # mobile only
  $scope.URLPattern = /^(ftp|http|https):\/\/[^ "]+$/

  inflector = $filter('pwgInflector')

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
    pw.password = pw.plaintextPassword

    onSuccess = ->
      pw.editing = false
      reissueLastSearch()

    pwgAjax.post url, pw, onSuccess

  reissueLastSearch = ->
    if $scope.lastSearch?
      if $scope.lastSearch is SEARCH_ALL_MARKER
        $scope.showAll()
      else
        $scope.searchTerm = $scope.lastSearch
        doSearch()

  deleteEntry = (pw) ->
    pwgModal.confirm("Really delete #{pw.name}?", "Confirm deletion").then ->
      url = routes.controllers.PasswordEntryController.delete(pw.id).url
      pwgAjax.delete url, null, ->
        reissueLastSearch()

  cancelEdit = (pw) ->
    _.extend pw, originalEntries[pw.id]
    pw.editing = false
    reissueLastSearch()

  createNew = (pw) ->
    url = routes.controllers.PasswordEntryController.create().url

    onSuccess = ->
      $scope.newPasswordEntry = null
      reissueLastSearch()

    pwgAjax.post url, $scope.newPasswordEntry, onSuccess

  $scope.selectedAny = ->
    if $scope.searchResults?
      first = _.find $scope.searchResults, (pw) -> pw.selected
      first?
    else
      false

  $scope.editingAny = ->
    if $scope.searchResults?
      first = _.find $scope.searchResults, (pw) -> pw.editing
      first?
    else
      false

  $scope.deleteSelected = ->
    if $scope.searchResults?
      toDelete = _.filter $scope.searchResults, (pw) -> pw.selected
      count = toDelete.length
      if count > 0
        pl = inflector(count, "entry", "entries")
        msg = "You are about to delete #{pl}. Are you sure?"
        pwgModal.confirm(msg, "Confirm deletion").then ->
          ids = _.map toDelete, (pw) -> pw.id
          url = routes.controllers.PasswordEntryController.deleteMany().url
          data =
            ids: ids
          pwgAjax.delete url, data, (response) ->
            pl = inflector(response.total, "entry", "entries")
            pwgFlash.info "Deleted #{pl}."
            reissueLastSearch()

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
      pw.selected         = false
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
                                          '$filter',
                                          InnerSearchCtrl]

# ---------------------------------------------------------------------------
# Profile controller
# ---------------------------------------------------------------------------

ProfileCtrl = ($scope, pwgLogging, pwgAjax, pwgFlash) ->

  log = pwgLogging.logger "ProfileCtrl"

  $scope.email          = $scope.loggedInUser?.email
  $scope.firstName      = $scope.loggedInUser?.firstName
  $scope.lastName       = $scope.loggedInUser?.lastName
  $scope.password1      = null
  $scope.password2      = null

  $scope.passwordsValid = (form) ->
    if form.password1.$pristine and form.password2.$pristine
      true
    else
      form.password1.$valid and form.password2.$valid and $scope.passwordsMatch()

  $scope.passwordsMatch = ->
    $scope.password1 is $scope.password2

  $scope.save = (form) ->
    data =
      firstName: $scope.firstName
      lastName:  $scope.lastName
      password1: $scope.password1
      password2: $scope.password2

    url = routes.controllers.UserController.save($scope.loggedInUser.id).url

    pwgAjax.post url, data, (response) ->
      log.debug "Save complete."
      $scope.setLoggedInUser response
      pwgFlash.info "Saved."
      form.$setPristine()


pwguardApp.controller 'ProfileCtrl', ['$scope',
                                      'pwgLogging',
                                      'pwgAjax',
                                      'pwgFlash',
                                      ProfileCtrl]

# ---------------------------------------------------------------------------
# Import/Export controllers
# ---------------------------------------------------------------------------

ImportExportCtrl = ($scope,
                    $timeout,
                    pwgAjax,
                    pwgFlash) ->

  #######################
  # Export              #
  #######################

  $scope.isExcel = -> $scope.exportFormat is 'xlsx'
  $scope.isCSV   = -> $scope.exportFormat is 'csv'

  $scope.downloading = false
  $scope.exportFormat = 'csv'
  $scope.formatPlaceholder = 'XXX'
  $scope.exportURLTemplate = routes.controllers.ImportExportController.exportData($scope.formatPlaceholder).url

  $scope.startDownload = ->
    $scope.downloading = true
    hide = ->
      $scope.downloading = false
    $timeout hide, 3000

  #######################
  # Import              #
  #######################

  $scope.importState = 'new'

  # ------------------- #
  # when state == 'new' #
  # ------------------- #

  $scope.importError = (msg) ->
    pwgFlash.error(msg)

  $scope.importFilename = null
  $scope.mimeType       = null
  $scope.importFile     = null
  $scope.fileDropped = (contents, name, mimeType) ->
    $scope.importFilename = name
    $scope.importFile = contents
    $scope.mimeType   = mimeType

  $scope.upload = ->
    url = routes.controllers.ImportExportController.importDataUpload().url
    data =
      filename: $scope.importFilename
      contents: $scope.importFile
      mimeType: $scope.mimeType

    pwgAjax.post url, data, (response) ->
      $scope.importState = 'mapping'
      prepareMappingData(response)

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
    matchedRequired = $scope.headers.filter (h) -> h.matchedTo?.required
    totalRequired.length is matchedRequired.length

  $scope.completeImport = ->
    data =
      mappings: {}

    for k in $scope.fields
      if k.matchedTo?
        data.mappings[k.name] = k.matchedTo.name

    url = routes.controllers.ImportExportController.completeImport().url
    pwgAjax.post url, data, (response) ->
      $scope.importState = 'complete'
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
    $scope.importState = 'new'
    $scope.progress = 0
    pwgFlash.clearAll()

pwguardApp.controller 'ImportExportCtrl', ['$scope',
                                           '$timeout',
                                           'pwgAjax',
                                           'pwgFlash',
                                           ImportExportCtrl]

# ---------------------------------------------------------------------------
# Admin users controller
# ---------------------------------------------------------------------------

AdminUsersCtrl = ($scope, pwgAjax, pwgFlash, pwgModal) ->
  $scope.users = null
  $scope.addingUser = null
  $scope.sortColumn = "email"
  $scope.reverse = false

  $scope.sortBy = (column) ->
    if column is $scope.sortColumn
      $scope.reverse = !$scope.reverse
    else
      $scope.reverse = false
      $scope.sortColumn = column

  originalUsers = {}

  saveUser = (u) ->
    url = routes.controllers.UserController.save(u.id).url

    onFailure = ->
      pwgFlash.error "Save failed."

    onSuccess = ->
      originalUsers[u.email] = _.omit 'save', 'cancel', 'edit', 'editing'
      u.editing = false
      loadUsers()

    pwgAjax.post url, u, onSuccess, onFailure

  cancelEdit = (u) ->
    _.extend u, originalUsers[u.email]
    u.editing = false

  deleteUser = (u) ->
    if u.id is $scope.loggedInUser.id
      pwgFlash.error "You can't delete yourself!"

    else
      pwgModal.confirm("Really delete #{u.email}?", "Confirm deletion").then ->
        url = routes.controllers.UserController.delete(u.id).url
        pwgAjax.delete url, null, ->
          loadUsers()

  $scope.passwordsMismatch = (user) ->
    !$scope.passwordsMatch(user)

  $scope.passwordsMatch = (user) ->
    user.password1 is user.password2

  createUser = (u) ->
    url = routes.controllers.UserController.create().url

    onSuccess = ->
      loadUsers()
      $scope.addingUser = null

    pwgAjax.post url, $scope.addingUser, onSuccess

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

  loadUsers()

pwguardApp.controller 'AdminUsersCtrl', ['$scope',
                                         'pwgAjax',
                                         'pwgFlash',
                                         'pwgModal',
                                         AdminUsersCtrl]

AboutCtrl = ($scope) ->
  return

pwguardApp.controller 'AboutCtrl', ['$scope', AboutCtrl]
