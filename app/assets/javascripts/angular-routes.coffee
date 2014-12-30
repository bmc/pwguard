# Angular routing. All routes are consolidated in this service.
# -----------------------------------------------------------------------------

pwgRoutesModule = angular.module('pwguard-routes', ['ngRoute'])

templateURL   = window.angularTemplateURL

ROUTES            = {}
POST_LOGIN_ROUTES = []
ADMIN_ONLY_ROUTES = []
REVERSE_ROUTES    = {}

config = ($routeProvider) ->

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

  otherwise           = null

  for url of ROUTES
    r = ROUTES[url]
    REVERSE_ROUTES[r.name] = '#' + url

    cfg =
      templateUrl: r.templateUrl
      controller:  r.controller

    $routeProvider.when url, r
    if r.defaultURL
      otherwise = url

    if r.postLogin
      POST_LOGIN_ROUTES.push r.name

    if r.admin
      ADMIN_ONLY_ROUTES.push r.name

  if otherwise
    cfg =
      redirectTo: otherwise
    $routeProvider.otherwise cfg

  console.log REVERSE_ROUTES

pwgRoutesModule.config ['$routeProvider', config]

pwgRoutes = ->

  console.log ADMIN_ONLY_ROUTES

  isAdminOnlyRoute: (name) ->
    name in ADMIN_ONLY_ROUTES

  isPostLoginRoute: (name) ->
    name in POST_LOGIN_ROUTES

  isPreLoginSegment: (name) ->
    not window.isPostLoginRoute(name)

  pathForRouteName: (name) ->
    REVERSE_ROUTES[name]

  hrefForRouteName: (name) ->
    "#" + pathForRouteName(name)

  routeNameForURL: (url) ->
    url = if url? then url else ""
    strippedURL = if url[0] is "#" then url[1..] else url
    result = null
    for r of REVERSE_ROUTES
      if REVERSE_ROUTES[r] is strippedURL
        result = r
        break
    result

pwgRoutesModule.factory 'pwgRoutes', [pwgRoutes]

