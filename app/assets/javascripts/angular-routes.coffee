# Angular routing.
#
# NOTE: This routing depends on the third-party angular-route-segment
# module. See http://angular-route-segment.com/
# -----------------------------------------------------------------------------

templateURL   = window.angularTemplateURL

# This table allows easy mappings from route segment to URL.
segments =
  "login":       "/login"
  "search":      "/search"
  "profile":     "/profile"
  "admin-users": "/admin/users"

# The routes themselves.
window.setRoutes = ($routeSegmentProvider, $routeProvider) ->
  $routeSegmentProvider.options.autoLoadTemplates = true

  # Map URLs to segment names. This is the actual routing table.

  for segment of segments
    $routeSegmentProvider.when(segments[segment], segment)

  # Define each segment's behavior and nesting.

  $routeSegmentProvider.segment "login",
    templateUrl: templateURL("login.html")
    controller:  'LoginCtrl'

  $routeSegmentProvider.segment "search",
    templateUrl: templateURL("search.html")
    controller:  'SearchCtrl'

  $routeSegmentProvider.segment "profile",
    templateUrl: templateURL("profile.html")
    controller:  'ProfileCtrl'

  $routeSegmentProvider.segment "admin-users",
    templateUrl: templateURL("admin-users.html")


  $routeProvider.otherwise
    redirectTo: "/search"

POST_LOGIN_SEGMENTS = ['search', 'profile', 'admin-users']
ADMIN_ONLY_SEGMENTS = ['admin-users']

# -----------------------------------------------------------------------------
# Utility functions
# -----------------------------------------------------------------------------

window.isAdminOnlySegment = (segment) ->
  segment in ADMIN_ONLY_SEGMENTS

window.isPostLoginSegment = (segment) ->
  segment in POST_LOGIN_SEGMENTS

window.isPreLoginSegment = (segment) ->
  not window.isPostLoginSegment(segment)

window.pathForSegment = (segment) ->
  segments[segment]

window.hrefForSegment = (segment) ->
  "#" + pathForSegment(segment)

window.segmentForURL = (url) ->
  url = if url? then url else ""
  strippedURL = if url[0] is "#" then url[1..] else url
  segment = null
  for seg of segments
    if segments[seg] is strippedURL
      segment = seg
      break
  segment

