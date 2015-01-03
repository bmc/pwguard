/*
  This code is ES6-based and must be transpiled with traceur. JSHint doesn't
  grok ES6, either, so it must be disabled for this file.
*/

/* jshint ignore:start */

"use strict";

import * as util from './util';
import * as filters from './filters';
import * as services from './services';
import * as directives from './directives';

util.init()


var pwGuardApp = angular.module("PWGuardApp", ['ngRoute',
                                               'ngCookies',
                                               'ngSanitize',
                                               'mgcrea.ngStrap',
                                               'pwguard-services',
                                               'pwguard-filters',
                                               'pwguard-directives']);

// ##########################################################################
// Utility functions
// ##########################################################################



// ##########################################################################
// Initialization logic
// ##########################################################################

var ROUTES                  = {};
var POST_LOGIN_ROUTES       = [];
var ADMIN_ONLY_ROUTES       = [];
var ALWAYS_AVAILABLE_ROUTES = [];
var REVERSE_ROUTES          = {};
var DEFAULT_ROUTE           = null;

function initializeRouting($routeProvider) {
  var templateURL = angularTemplateURL;

  ROUTES = {
    "/initializing": {
      templateUrl:  templateURL("initializing.html"),
      controller:   'InitCtrl',
      name:         'initializing',
      admin:        false,
      alwaysActive: true
    },
    "/login": {
      templateUrl: templateURL("login.html"),
      controller:  'LoginCtrl',
      name:        'login',
      admin:       false,
      postLogin:   false
    },
    "/search": {
      templateUrl: templateURL("search.html"),
      controller:  'SearchCtrl',
      name:        'search',
      admin:       false,
      postLogin:   true,
      defaultURL:  true
    },
    "/profile": {
      templateUrl: templateURL("profile.html"),
      controller:  'ProfileCtrl',
      name:        'profile',
      admin:       false,
      postLogin:   true
    },
    "/admin/users": {
      templateUrl: templateURL("admin-users.html"),
      controller:  'AdminUsersCtrl',
      name:        'admin-users',
      admin:       true,
      postLogin:   true
    },
    "/import-export": {
      templateUrl: templateURL("ImportExport.html"),
      controller:  'ImportExportCtrl',
      name:        'import-export',
      admin:       false,
      postLogin:   true
    },
    "/about": {
      templateUrl: templateURL("about.html"),
      controller:  'AboutCtrl',
      name:        'about',
      admin:       false,
      postLogin:   true
    }
  }

  for (var url in ROUTES) {
    var r = ROUTES[url];
    REVERSE_ROUTES[r.name] = url;

    $routeProvider.when(url, {
      templateUrl: r.templateUrl,
      controller:  r.controller
    });

    if (r.defaultURL) DEFAULT_ROUTE = r.name;
    if (r.postLogin) POST_LOGIN_ROUTES.push(r.name);
    if (r.admin) ADMIN_ONLY_ROUTES.push(r.name);
    if (r.alwaysActive) ALWAYS_AVAILABLE_ROUTES.push(r.name);
  }

  if (DEFAULT_ROUTE) {
    $routeProvider.otherwise({
      redirectTo: DEFAULT_ROUTE
    });
  }
}

function checkMissingFeatures() {
  var missing = [];

  if (! (FileReader && Modernizr.draganddrop)) missing.push("drag-and-drop");
  if (! Modernizr.canvastext) missing.push("canvas");

  if (missing.length > 0) {
    alert("Your browser lacks the following HTML 5 features:\n\n" +
          missing.join(', ') + "\n\n" +
          "Parts of this application may not behave as designed or may fail " +
          "completely. Please consider using a newer browser.");
  }
}

pwGuardApp.config(['$routeProvider', function($routeProvider) {
  initializeRouting($routeProvider);
  checkMissingFeatures();
}]);

// ##########################################################################
// Services
// ##########################################################################

var pwgRoutes = pwGuardApp.factory('pwgRoutes', ['$injector', function($injector) {

     var pwgLogging = $injector.get('pwgLogging');
     var pwgError   = $injector.get('pwgError');
     var pwgFlash   = $injector.get('pwgFlash');
     var $location  = $injector.get('$location');
     var $route     = $injector.get('$route');

     var log = pwgLogging.logger("pwgRoutes");
     var URL_RE = /^.*#(.*)$/;

     var isPostLoginRoute = (name) => {
       return (POST_LOGIN_ROUTES.indexOf(name) >= 0) ||
              (ALWAYS_AVAILABLE_ROUTES.indexOf(name) >= 0);
     }

     var isPreLoginRoute = (name) => {
       return (POST_LOGIN_ROUTES.indexOf(name) < 0) ||
              (ALWAYS_AVAILABLE_ROUTES.indexOf(name) >= 0);
     }

     var pathForRouteName = (name) => {
       return REVERSE_ROUTES[name];
     }

     var redirectToNamedRoute = (name) => {
       var url = REVERSE_ROUTES[name];
       if (url) {
         log.debug(`Redirecting to ${url}`)
         pwgFlash.clearAll();
         $location.path(url);
       }
       else {
         pwgError.showStackTrace(`(BUG) No URL for route ${name}`)
       }
     }

     return {
       isAdminOnlyRoute: (name) => {
         return ADMIN_ONLY_ROUTES.indexOf(name) >= 0;
       },

       isPostLoginRoute: (name) => {
         return isPostLoginRoute(name);
       },

       isPreLoginRoute: (name) => {
         return isPreLoginRoute(name);
       },

       hrefForRouteName: (name) => {
         return "#" + pathForRouteName(name);
       },

       pathForRouteName: (name) => {
         return pathForRouteName(name);
       },

       defaultRoute: () => { return DEFAULT_ROUTE; },

       redirectToDefaultRoute: () => {
         redirectToNamedRoute(DEFAULT_ROUTE);
       },

       routeIsActive: (name) => {
         var path = pathForRouteName(name);
         return (path && $location.path().endsWith(path));
       },

       redirectToNamedRoute: (name) => {
         redirectToNamedRoute(name);
       },

       routeNameForURL: (url) => {
         if (!url) url = "";
         var m = URL_RE.exec(url);
         var strippedURL;
         if (m)
           strippedURL = m[1];
         else
           strippedURL = url;

         var result = null;
         for (var r in REVERSE_ROUTES) {
           if (REVERSE_ROUTES[r] === strippedURL) {
             result = r;
             break;
           }
         }

         return result;
       }
     }
   }
  ]
);

// ##########################################################################
// Controllers
// ##########################################################################

// --------------------------------------------------------------------------
// Init Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('InitCtrl', ['$scope', 'pwgLogging',
  function($scope, pwgLogging) {
  }]
)

// --------------------------------------------------------------------------
// Main Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('MainCtrl', ['$scope', '$injector',
   function($scope, $injector) {

     var $rootScope         = $injector.get('$rootScope');
     var $location          = $injector.get('$location');
     var pwgTimeout         = $injector.get('pwgTimeout');
     var pwgAjax            = $injector.get('pwgAjax');
     var pwgFlash           = $injector.get('pwgFlash');
     var pwgCheckUser       = $injector.get('pwgCheckUser');
     var pwgLogging         = $injector.get('pwgLogging');
     var pwgRoutes          = $injector.get('pwgRoutes');
     var angularTemplateURL = $injector.get('angularTemplateURL');
     var pwgError           = $injector.get('pwgError');


     // Put the template URL in the scope, because it's used inside templates
     // (e.g., within ng-include directives).
     $scope.templateURL = angularTemplateURL;
     $scope.version     = window.version;

     $scope.debugMessages = [];
     $scope.debug = (msg) => {
       $scope.debugMessages.push(msg);
     }

     var log = pwgLogging.logger("MainCtrl");

     $scope.dialogConfirmTitle    = null;
     $scope.dialogConfirmMessage  = null;
     $scope.loggedInUser          = null;
     $scope.routeOnLoad           = pwgRoutes.routeNameForURL($location.path());
     $scope.initializing          = true;
     $scope.flashAfterRouteChange = null;
     $scope.isMobile              = window.browserIsMobile;

     $scope.routeIsActive = pwgRoutes.routeIsActive;

     pwgAjax.on401(function() {
       if ($scope.loggedInUser) {
         $scope.loggedInUser = null;
         pwgRoutes.redirectToNamedRoute('login');
         $scope.flashAfterRouteChange = "Session timeout. Please log in again.";
       }
       else {
         pwgFlash.error("Login failure");
       }
     });

     $scope.$on('$routeChangeSuccess', () => {
       // Skip, while initializing. (Doing this during initialization screws
       // things up, causing multiple redirects that play games with Angular.)
       if(! $scope.initializing) {
         var routeName = pwgRoutes.routeNameForURL($location.path());
         var useRoute = validateLocationChange(routeName);
         log.debug(`routeName=${routeName}, useRoute=${useRoute}`);
         if (useRoute !== routeName)
           pwgRoutes.redirectToNamedRoute(useRoute);
       }
     });

     $scope.loggedIn = () => {
       if ($scope.loggedInUser)
         return true;
       else
         return false;
     }

     $scope.setLoggedInUser = (user) => {
       if (user && user.email)
         $scope.loggedInUser = user;
       else
         $scope.loggedInUser = null;
     }

     // On initial load or reload, we need to determine whether the user is
     // still logged in, since a reload clears everything in the browser.

     function validateLocationChange(routeName) {
       var useRoute = null;
       if ($scope.loggedInUser) {
         // Ensure that the segment is valid for the logged in user.
         useRoute = pwgRoutes.defaultRoute();
         if (routeName && pwgRoutes.isPostLoginRoute(routeName)) {
           if ($scope.loggedInUser.admin) {
             // Admins can go anywhere.
             useRoute = routeName;
           }
           else if (! pwgRoutes.isAdminOnlyRoute(routeName)) {
             // Non-admins can go to non-admin segments only.
             useRoute = routeName;
           }
         }
       }
       else {
         // #nsure that the segment is valid for a non-logged in user.
         if (routeName && pwgRoutes.isPreLoginRoute(routeName))
           useRoute = routeName;
         else
           useRoute = 'login';
       }

       return useRoute;
     }

     pwgRoutes.redirectToNamedRoute('initializing');

     pwgCheckUser.checkUser().then(
       function(response) {
         // Success
         $scope.initializing = false;
         if (response.loggedIn)
           $scope.setLoggedInUser(response.user);
         else
           $scope.setLoggedInUser(null);

         var useRoute = validateLocationChange($scope.routeOnLoad);
         pwgRoutes.redirectToNamedRoute(useRoute);
         $scope.routeOnLoad = null;
       },
       function(response) {
         // Failure.
         $scope.initializing = false;
         $scope.setLoggedInUser(null);
         pwgRoutes.redirectToNamedRoute('login');
       }
     );
   }]
);

// --------------------------------------------------------------------------
// Navbar Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('NavbarCtrl',
  ['$scope', '$injector', function($scope, $injector) {

    var pwgAjax   = $injector.get('pwgAjax');
    var pwgModal  = $injector.get('pwgModal');
    var pwgRoutes = $injector.get('pwgRoutes');

    $scope.logout = () => {

      pwgModal.confirm("Really log out?", "Confirm logout").then(
        function() {
          if ($scope.loggedIn()) {
            var url = routes.controllers.SessionController.logout().url

            var always = () => {
              $scope.setLoggedInUser(null);
              pwgRoutes.redirectToNamedRoute('login');
            }

            pwgAjax.post(url, {},
              function(response) {
                // Success
                always();
              },

              function(response) {
                // Failure
                console.log(`WARNING: Server logout error: ${response.status}`)
                always();
              }
            )
          }
        },

        // Rejection function.
        function() {
          // Rejected. Nothing to do.
        }
      )
    }

  }]
)

// --------------------------------------------------------------------------
// Login Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('LoginCtrl',
  ['$scope', '$injector', function($scope, $injector) {

     var pwgAjax            = $injector.get('pwgAjax');
     var pwgFlash           = $injector.get('pwgFlash');
     var pwgLogging         = $injector.get('pwgLogging');
     var pwgRoutes          = $injector.get('pwgRoutes');

     var log = pwgLogging.logger('LoginCtrl');

     $scope.email    = null;
     $scope.password = null;

     $scope.login = () => {
       var url = routes.controllers.SessionController.login().url;
       pwgAjax.post(url, {email: $scope.email, password: $scope.password},

         // Success.
         function(response) {
           $scope.setLoggedInUser(response.user);
           pwgRoutes.redirectToDefaultRoute();
         },

         // Failure
         function(response) {
           log.error(data);
           // Nothing to do. Error was handled by pwgAjax
         }
       );
     }

   }]
)

// --------------------------------------------------------------------------
// Search Controllers
// --------------------------------------------------------------------------

// Outer controller. Multiple copies of the inner controller can be
// instantiated. Only one outer controller will be.

pwGuardApp.controller('SearchCtrl', function() {
  return;
});

pwGuardApp.controller('InnerSearchCtrl',
  ['$scope', '$injector', function($scope, $injector) {

     $scope.searchTerm    = null;
     $scope.searchResults = null;
     $scope.lastSearch    = null;
     $scope.activePanel   = -1; // mobile only
     $scope.URLPattern    = /^(ftp|http|https):\/\/[^ "]+$/;
     $scope.sortColumn    = 'name';
     $scope.reverse       = false;

     var pwgAjax    = $injector.get('pwgAjax');
     var pwgFlash   = $injector.get('pwgFlash');
     var pwgTimeout = $injector.get('pwgTimeout');
     var pwgModal   = $injector.get('pwgModal');
     var pwgLogging = $injector.get('pwgLogging');
     var $filter    = $injector.get('$filter');
     var inflector  = $filter('pwgInflector');

     var SEARCH_ALL_MARKER = '-*-all-*-';

     var originalEntries = {};

     var pluralizeCount = (count) => {
       return inflector(count, "entry", "entries");
     }

    var validSearchTerm = () => {
      var trimmed = "";
      if ($scope.searchTerm)
        trimmed = $scope.searchTerm.trim();
      return trimmed.length >= 2;
    }

     $scope.pluralizeResults = function(n) { return pluralizeCount(n); }

     var clearResults = () => {
       originalEntries = {};
       $scope.searchResults = null;
     }

     $scope.searchTermChanged = () => {
       if (validSearchTerm())
         doSearch();
       else
         clearResults();
     }

     $scope.mobileSelect = (i) => {
       $(`#results-${i}`).select();
     }


     var doSearch = () => {
       originalEntries = {};
       $scope.newPasswordEntry = null;
       var url = routes.controllers.PasswordEntryController.searchPasswordEntries().url;
       pwgAjax.post(url, {searchTerm: $scope.searchTerm}, function(response) {
         $scope.lastSearch = $scope.searchTerm;
         $scope.searchResults = adjustResults(response.results);
       });
     }

     $scope.showAll = () => {
       $scope.newPasswordEntry = null;
       $scope.searchTerm       = null;

       var url = routes.controllers.PasswordEntryController.all().url;

       pwgAjax.get(url,
         function(response) {
           $scope.lastSearch = SEARCH_ALL_MARKER;
           $scope.searchResults = adjustResults(response.results);
         }
       );
     }

     $scope.sortBy = (column) => {
       if (column === $scope.sortColumn) {
         $scope.reverse = !$scope.reverse;
       }
       else {
         $scope.sortColumn = column;
         $scope.reverse    = false;
       }
     }

     var reissueLastSearch = () => {
       if ($scope.lastSearch) {
         if ($scope.lastSearch === SEARCH_ALL_MARKER) {
           $scope.showAll();
         }
         else {
           $scope.searchTerm = $scope.lastSearch;
           doSearch();
         }
       }
     }

     var saveEntry = (pw) => {
       var url = routes.controllers.PasswordEntryController.save(pw.id).url;
       pw.password = pw.plaintextPassword;

       pwgAjax.post(url, pw, function(response) {
         pw.editing = false;
         reissueLastSearch();
       });
     }

     var deleteEntry = (pw) => {
       console.log("Delete"); console.log(pw);
       pwgModal.confirm(`Really delete ${pw.name}?`, "Confirm deletion").then(
         function() {
           var url = routes.controllers.PasswordEntryController.delete(pw.id).url;
           pwgAjax.delete(url, {}, reissueLastSearch);
         }
       )
     }

     var cancelEdit = (pw) => {
       _.extend(pw, originalEntries[pw.id]);
       pw.editing = false;
       reissueLastSearch();
     }

     var createNew = (pw) => {
       var url = routes.controllers.PasswordEntryController.create().url;

       pwgAjax.post(url, $scope.newPasswordEntry,
         function() {
           $scope.newPasswordEntry = null;
           reissueLastSearch();
         }
       )
     }

     $scope.toggleSelectForAll = () => {
       for (var pw of $scope.searchResults) {
         pw.selected = !pw.selected;
       }
     }

     $scope.selectedAny = () => {
       var result = false;
       if ($scope.searchResults) {
         var first = _.find($scope.searchResults, (p) => { return p.selected });
         if (first) result = true;
       }
       return result;
     }

     $scope.editingAny = () => {
       var result = false;
       if ($scope.searchResults) {
         var first = _.find($scope.searchResults, (p) => { return p.editing });
         if (first) result = true;
       }
       return result;
     }

     $scope.deleteSelected = () => {
       if ($scope.searchResults) {
         var toDel = _.filter($scope.searchResults, (p) => { return p.selected });
         var count = toDel.length;
         if (count > 0) {
           var pl = pluralizeCount(count);
           pwgModal.confirm(`You are about to delete ${pl}. Are you sure?`,
                            "Confirm deletion").then(function() {
             var ids = _.map(toDel, (pw) => { return pw.id });
             var url = routes.controllers.PasswordEntryController.deleteMany().url;
             pwgAjax.delete(url, {ids: ids}, function(response) {
               pl = pluralizeCount(response.total);
               pwgFlash.info(`Deleted ${pl}.`);
               reissueLastSearch();
             });
           });
         }
       }
     }

     $scope.editNewEntry = () => {
       $scope.newPasswordEntry = {
         id:           null,
         name:         "",
         loginID:      "",
         password:     "",
         description:  "",
         url:          "",
         notes:        "",
         save:         () => { createNew(this); },
         cancel:       () => {
           $scope.newPasswordEntry = null;
           reissueLastSearch();
         }
       }

       $scope.searchResults = null;
     }

     var adjustResults = (results) => {
       originalEntries = {};
       var r = _.map(results, (pw) => {
         pw.showPassword     = false;
         pw.editing          = false;
         pw.notesPreview     = util.ellipsize(pw.notes);
         pw.previewAvailable = ! (pw.notes === pw.notesPreview);
         pw.showPreview      = pw.previewAvailable;
         pw.passwordVisible  = false;
         pw.selected         = false;
         pw.toggleVisibility = () => {
           pw.passwordVisible = !pw.passwordVisible;
         }

         originalEntries[pw.id] = pw;

         pw.edit   = function() { this.editing = true; }
         pw.cancel = function() { cancelEdit(this); }
         pw.save   = function() { saveEntry(this); }
         pw.delete = function() { deleteEntry(this); }

         return pw;
       });

       return r;
     }
   }]
)

// --------------------------------------------------------------------------
// Profile Controllers
// --------------------------------------------------------------------------

pwGuardApp.controller('ProfileCtrl',
  ['$scope', '$injector', function($scope, $injector) {

    var pwgLogging = $injector.get('pwgLogging');
    var pwgAjax    = $injector.get('pwgAjax');
    var pwgFlash   = $injector.get('pwgFlash');

    var log = pwgLogging.logger('ProfileCtrl');

    if (! $scope.loggedInUser) {
      $scope.email     = null;
      $scope.firstName = null;
      $scope.lastName  = null;
    }
    else {
      $scope.email     = $scope.loggedInUser.email;
      $scope.firstName = $scope.loggedInUser.firstName;
      $scope.lastName  = $scope.loggedInUser.lastName;
    }

    $scope.password1 = null;
    $scope.password2 = null;

    $scope.passwordsValid = (form) => {
      if (form.password1.$pristine && form.password2.$pristine)
        return true;
      else
        return (form.password1.$valid &&
                form.password2.$valid &&
                $scope.passwordsMatch());
    }

    $scope.passwordsMatch = () => {
      return $scope.password1 === $scope.password2;
    }

    $scope.save = (form) => {
      var data = {
        firstName:  $scope.firstName,
        lastName:   $scope.lastName,
        password1:  $scope.password1,
        password2:  $scope.password2
      }

      var url = routes.controllers.UserController.save($scope.loggedInUser.id).url

      pwgAjax.post(url, data, (response) => {
        log.debug("Save complete.");
        $scope.setLoggedInUser(response);
        pwgFlash.info("Saved.");
        form.$setPristine();
      });
    }
  }]
)

// --------------------------------------------------------------------------
// Import/Export Controllers
// --------------------------------------------------------------------------

pwGuardApp.controller('ImportExportCtrl',
  ['$scope', '$timeout', 'pwgAjax', 'pwgFlash', 'pwgLogging',
  function($scope, $timeout, pwgAjax, pwgFlash, pwgLogging) {
    var log = pwgLogging.logger('ImportExportCtrl');

    // ****** //
    // Export //
    // ****** //

    $scope.isExcel = function() { return $scope.exportFormat === 'xlsx'; }
    $scope.isCSV   = function() { return $scope.exportFormat === 'csv'; }

    $scope.downloading = false;
    $scope.exportFormat = 'csv';
    $scope.formatPlaceholder = 'XXX';
    $scope.exportURLTemplate = routes.controllers.ImportExportController.exportData($scope.formatPlaceholder).url;
console.log($scope.exportURLTemplate);

    $scope.startDownload = function() {
      $scope.downloading = true;
      $timeout(function() { $scope.downloading = false; }, 3000);
    }

    // ****** //
    // Import //
    // ****** //

    $scope.importState = 'new';

    // --------------------------
    // when importState is 'new'
    // --------------------------

    $scope.importError = (msg) => { pwgFlash.error(msg); }

    $scope.importFilename = null;
    $scope.mimeType       = null;
    $scope.importFile     = null;

    $scope.fileDropped = (contents, name, mimeType) => {
      $scope.importFilename = name;
      $scope.importFile     = contents;
      $scope.mimeType       = mimeType;
      log.debug(`Dropped: filename=${name}, MIME type=${mimeType}`)
    }

    $scope.upload = () => {
      var url = routes.controllers.ImportExportController.importDataUpload().url;
      var data = {
        filename: $scope.importFilename,
        contents: $scope.importFile,
        mimeType: $scope.mimeType
      }

      pwgAjax.post(url, data, (response) => {
        $scope.importState = 'mapping';
        prepareMappingData(response);
      });
    }

    // ------------------------------
    // when importState is 'mapping'
    // ------------------------------

    var prepareMappingData = (data) => {
      $scope.headers = _.map(data.headers, (h) => {
        var obj = {
          name:      h,
          matchedTo: null,
          selected:  false
        }

        obj.select = () => {
          toggleSelection(obj, $scope.headers);
        }

        return obj;
      });

      $scope.fields = _.map(data.fields, (f) => {
        var obj = {
          name:      f.name,
          matchedTo: null,
          selected:  false,
          required:  f.required
        }

        obj.select  = () => { toggleSelection(obj, $scope.fields) }
        obj.unmatch = () => { unmatch(obj) }

        return obj;
      });

      // Pre-match on name.

      for (var f of $scope.fields) {
        $scope.fields[f.name.toLowerCase()] = f
      }

      for (var h of $scope.headers) {
        var matchedField = $scope.fields[h.name.toLowerCase()];
        if (matchedField) {
          matchedField.matchedTo = h;
          h.matchedTo = matchedField;
        }
      }
    }

    var checkForMatch = () => {
      if ($scope.headers && $scope.filters) {
        var i = _.filter($scope.headers, (h) => { return h.selected });
        var selectedHeader = i[0];
        if (selectedHeader) {
          i = _.filter($scope.fields, (f) => { return f.selected });
          var selectedField = i[0];

          if (selectedField) {
            selectedHeader.matchedTo = selectedField;
            selectedField.matchedTo  = selectedHeader;
            selectedHeader.selected  = false;
            selectedField.selected   = false;
          }
        }
      }
    }

    var toggleSelection = (item, list) => {
      var v = ! item.selected;
      for (var other of list) {
        other.selected = false;
      }

      item.selected = v;
      checkForMatch();
    }

    var unmatch = (item) => {
      if (item.matchedTo) item.matchedTo.matchedTo = null;
      item.matchedTo = null;
    }

    $scope.availableItem = (item) => { return ! (item.matchedTo) }

    $scope.matchedItem = (item) => { return item.matchedTo }

    $scope.allMatched = () => {
      var totalRequired = [];
      var matchedRequired = [];

      if ($scope.fields) {
        var totalRequired = _.filter($scope.fields, (f) => {
          return f.required
        });
      }

      if ($scope.headers) {
        var matchedRequired = _.filter($scope.headers, (h) => {
          var res = false;
          if (h.matchedTo) res = h.matchedTo.required;
          return res;
        });
      }

      return (totalRequired.length === matchedRequired.length);
    }

    $scope.completeImport = () => {
      var data = {
        mappings: {}
      }

      for (var k of $scope.fields) {
        if (k.matchedTo)
          data.mappings[k.name] = k.matchedTo.name;
      }

      var url = routes.controllers.ImportExportController.completeImport().url;
      pwgAjax.post(url, data, (response) => {
        $scope.importState = 'complete';
        handleCompletion(response.total);
      })
    }

    // ------------------------------
    // when importState is 'complete'
    // ------------------------------

    $scope.completionCount = 0
    var handleCompletion = (total) => {
      switch(total) {
        case 0:
          $scope.completionCount = "no new entries";
          break;
        case 1:
          $scope.completionCount = "one new entry";
          break;
        default:
          $scope.completionCount = `${total} new entries`;
          break;
      }
    }

    $scope.reset = () => {
      $scope.importState = 'new';
      pwgFlash.clearAll();
    }
  }]
)

// --------------------------------------------------------------------------
// Admin Users Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('AdminUsersCtrl',
  ['$scope', '$injector', function($scope, $injector) {

    var pwgAjax    = $injector.get('pwgAjax');
    var pwgFlash   = $injector.get('pwgFlash');
    var pwgLogging = $injector.get('pwgLogging');
    var pwgModal   = $injector.get('pwgModal');

    $scope.users      = null;
    $scope.addingUser = null;
    $scope.sortColumn = "email";
    $scope.reverse    = false;

    $scope.sortBy = (column) => {
      if (column === $scope.sortColumn) {
        $scope.reverse = !$scope.reverse
      }
      else {
        $scope.reverse = false;
        $scope.sortColumn = column;
      }
    }

    var originalUsers = {};

    var saveUser = (u) => {
      var url = routes.controllers.UserController.save(u.id).url;

      pwgAjax.post(url, u, (response) => {
        originalUsers[u.email] = _.omit('save', 'cancel', 'edit', 'editing');
        u.editing = false;
        loadUsers();
      });
    }

    var cancelEdit = (u) => {
      _.extend(u, originalUsers[u.email]);
      u.editing = false;
    }

    var deleteUser = (u) => {
      if (u.id === $scope.loggedInUser.id)
        pwgFlash.error("You can't delete yourself!");
      else {
        pwgModal.confirm(`Really delete ${u.email}?`, "Confirm deletion").then(
          function() {
            var url = routes.controllers.UserController.delete(u.id).url
            pwgAjax.delete(url, {}, (response) => { loadUsers() });
          }
        )
      }
    }

    $scope.passwordsMismatch = (user) => {
      return !$scope.passwordsMatch(user);
    }

    $scope.passwordsMatch = (user) => {
      return (user.password1 === user.password2);
    }

    var createUser = (u) => {
      var url = routes.controllers.UserController.create().url;

      pwgAjax.post(url, $scope.addingUser, (response) => {
        loadUsers();
        $scope.addingUser = null;
      });
    }

    $scope.editingAny = () => {
      var res = false;
      if ($scope.users) {
        var first = _.find($scope.users, (u) => { return u.editing });
        if (first) res = true;
      }

      return res;
    }

    $scope.editNewUser = () => {
      $scope.addingUser = {
        id:             null,
        email:          "",
        password1:      "",
        password2:      "",
        firstName:      "",
        lastName:       "",
        admin:          false,
        active:         true,
        editing:        true,
        isNew:          true,
        passwordsMatch: true,
        cancel:         () => { $scope.addingUser = null },
        save:           () => { createUser(this) },
        clear:          () => {
          $scope.addingUser.email     = null;
          $scope.addingUser.password1 = null;
          $scope.addingUser.password2 = null;
          $scope.addingUser.firstName = null;
          $scope.addingUser.lastName  = null;
          $scope.addingUser.active    = true;
          $scope.addingUser.admin     = false;
        }
      }
    }

    var canSave = (u) => { return checkSave(u) !== null; }

    var loadUsers = () => {
      originalUsers = {};
      $scope.users = null;
      var url = routes.controllers.UserController.getAll().url;

      pwgAjax.get(url, (response) => {
        $scope.users = _.map(response.users, (u) => {
          if ($scope.loggedInUser && (u.id === $scope.loggedInUser.id)) {
            $scope.setLoggedInUser(u); // Update info for current user
          }

          var u2 = _.clone(u);
          u2.editing   = false;
          u2.password1 = "";
          u2.password2 = "";
          u2.isNew     = false;

          originalUsers[u.email] = _.clone(u);

          u2.edit    = function() { this.editing = true };
          u2.save    = function() { saveUser(this) };
          u2.cancel  = function() { cancelEdit(this) };
          u2.delete  = function() { deleteUser(this) };
          u2.canSave = function() { canSave(this) };

          u2.passwordsMatch = true;

          return u2;
        })
      })
    }

    loadUsers();
  }]
);


// --------------------------------------------------------------------------
// About Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('AboutCtrl', ['$scope', function($scope) {
  return;
}])


/* jshint ignore:end */
