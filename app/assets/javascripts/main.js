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

function initializeRouting($routeProvider) {
  var templateURL = angularTemplateURL;

  $routeProvider.
    when("/initializing", {
      templateUrl:  templateURL("initializing.html"),
      controller:   'InitCtrl',
      name:         'initializing',
      admin:        false,
      alwaysActive: true
    }).
    when(
    "/login", {
      templateUrl: templateURL("login.html"),
      controller:  'LoginCtrl',
      name:        'login',
      admin:       false,
      postLogin:   false
    }).
    when("/search", {
      templateUrl: templateURL("search.html"),
      controller:  'SearchCtrl',
      name:        'search',
      admin:       false,
      postLogin:   true,
    }).
    when("/edit/:id", {
      templateUrl: templateURL("edit-password-entry.html"),
      controller:  'EditPasswordEntryCtrl',
      name:        'edit-entry',
      admin:       false,
      postLogin:   true
    }).
    when("/new-entry", {
      templateUrl: templateURL("new-password-entry.html"),
      controller:  'NewPasswordEntryCtrl',
      name:        'new-entry',
      admin:       false,
      postLogin:   true
    }).
    when("/profile", {
      templateUrl: templateURL("profile.html"),
      controller:  'ProfileCtrl',
      name:        'profile',
      admin:       false,
      postLogin:   true
    }).
    when("/admin/users", {
      templateUrl: templateURL("admin-users.html"),
      controller:  'AdminUsersCtrl',
      name:        'admin-users',
      admin:       true,
      postLogin:   true
    }).
    when("/import-export", {
      templateUrl: templateURL("ImportExport.html"),
      controller:  'ImportExportCtrl',
      name:        'import-export',
      admin:       false,
      postLogin:   true
    }).
    when("/about", {
      templateUrl: templateURL("about.html"),
      controller:  'AboutCtrl',
      name:        'about',
      admin:       false,
      postLogin:   true
    }).
    otherwise({
      redirectTo:  "/search",
      defaultName: "search",
      isDefault:   true
    });
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

var pwgRoutes = pwGuardApp.factory('pwgRoutes',
  ['$injector', function($injector) {

    var pwgLogging              = $injector.get('pwgLogging');
    var pwgError                = $injector.get('pwgError');
    var pwgFlash                = $injector.get('pwgFlash');
    var $location               = $injector.get('$location');
    var $route                  = $injector.get('$route');
    var DEFAULT_ROUTE_NAME      = null;
    var POST_LOGIN_ROUTES       = [];
    var ADMIN_ONLY_ROUTES       = [];
    var ALWAYS_AVAILABLE_ROUTES = [];
    var REVERSE_ROUTES          = {};

    var log = pwgLogging.logger("pwgRoutes");

    for (var pattern in $route.routes) {
      let route = $route.routes[pattern];
      if (!route)
        continue;

      if (route.isDefault) { // This is the default route
        DEFAULT_ROUTE_NAME = route.defaultName;
      }
      else if (route.name) {
        // AngularJS adds routes. If the name field is present, it's one
        // we added.
        REVERSE_ROUTES[route.name] = pattern;

        if (route.postLogin) POST_LOGIN_ROUTES.push(route.name);
        if (route.admin) ADMIN_ONLY_ROUTES.push(route.name);
        if (route.alwaysActive) ALWAYS_AVAILABLE_ROUTES.push(route.name);
      }
    }

    var URL_RE = /^.*#(.*)$/;

    var isPostLoginRoute = (name) => {
      return (POST_LOGIN_ROUTES.indexOf(name) >= 0) ||
             (ALWAYS_AVAILABLE_ROUTES.indexOf(name) >= 0);
    }

    var isPreLoginRoute = (name) => {
      return (POST_LOGIN_ROUTES.indexOf(name) < 0) ||
             (ALWAYS_AVAILABLE_ROUTES.indexOf(name) >= 0);
    }

    var substituteParams = (url, params) => {
      for (let k in params) {
        url = url.replace(":" + k, params[k]);
      }
      return url;
    }

    var pathForRouteName = (name, params = {}) => {
      let urlPattern = REVERSE_ROUTES[name];
      if (urlPattern) {
        return substituteParams(urlPattern, params);
      }
      else {
        throw new Error(`(BUG) No URL for route ${name}`)
      }
    }

    var hrefForRouteName = (name, params = {}) => {
      return "#" + pathForRouteName(name, params);
    }

    var redirectToNamedRoute = (name, params = {}) => {
     let url = pathForRouteName(name, params);
     log.debug(`Redirecting to ${url}`)
     pwgFlash.clearAll();
     $location.path(url);
    }

    var DEFAULT_ROUTE_PATH = pathForRouteName(DEFAULT_ROUTE_NAME);
    var DEFAULT_ROUTE_HREF = hrefForRouteName(DEFAULT_ROUTE_NAME);

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

      hrefForRouteName: (name, params = {}) => {
        return hrefForRouteName(name, params);
      },

      pathForRouteName: (name) => {
        return pathForRouteName(name);
      },

      // These are functions to ensure no one can modify the values.
      defaultRouteName: () => { return DEFAULT_ROUTE_NAME; },

      defaultRoutePath: () => { return pathForRouteName(DEFAULT_ROUTE_NAME); },

      defaultRouteHref: () => { return hrefForRouteName(DEFAULT_ROUTE_NAME); },

      redirectToDefaultRoute: () => {
        redirectToNamedRoute(DEFAULT_ROUTE_NAME);
      },

      routeIsActive: (name) => {
        let path = pathForRouteName(name);
        return (path && $location.path().endsWith(path));
      },

      redirectToNamedRoute: (name, params = {}) => {
        redirectToNamedRoute(name, params);
      },

      routeNameForURL: (url) => {
        if (!url) url = "";
        let m = URL_RE.exec(url);
        let strippedURL;
        if (m)
          strippedURL = m[1];
        else
          strippedURL = url;

        let result = null;
        for (var pattern in $route.routes) {
          let r = $route.routes[pattern];
          if (! r.name)
            continue;
          if (! r.regexp)
            continue;
          if (r.regexp.test(strippedURL)) {
            result = r.name;
            break;
          }
        }

        return result;
      }
    }
  }
]);

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
     var pwgModal           = $injector.get('pwgModal');

     // Put the template URL in the scope, because it's used inside templates
     // (e.g., within ng-include directives).

     $scope.URLPattern  = /^(ftp|http|https):\/\/[^ "]+$/;
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
     $scope.urlOnLoad             = $location.path();
     $scope.initializing          = true;
     $scope.flashAfterRouteChange = null;
     $scope.isMobile              = window.browserIsMobile;
     $scope.defaultHref           = pwgRoutes.defaultRouteHref();

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

     $scope.$on('$routeChangeStart', () => {
       // Skip, while initializing. (Doing this during initialization screws
       // things up, causing multiple redirects that play games with Angular.)
       if(! $scope.initializing) {
         let url = $location.path();
         let useUrl = validateLocationChange(url);
         if (useUrl !== url)
           $location.path(useUrl);
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


     // Check a form. If it's been edited, prompt the user for cancellation.
     // If the user confirms, or if the form is pristine, route to the named
     // location.
     $scope.validateFormCancellation = function(form, routeName) {
       let doCancel = function() {
         pwgRoutes.redirectToNamedRoute('search');
       }

       if (form.$dirty) {
         pwgModal.confirm("You've modified the form. Really cancel?",
                          "Confirm cancel.").then(doCancel);
       }
       else {
         doCancel();
       }
     }

     // On initial load or reload, we need to determine whether the user is
     // still logged in, since a reload clears everything in the browser.

     function validateLocationChange(url) {
       let useUrl = null;
       let routeName = pwgRoutes.routeNameForURL(url);

       if ($scope.loggedInUser) {
         // Ensure that the segment is valid for the logged in user.
         useUrl = pwgRoutes.defaultRouteName();
         if (url && pwgRoutes.isPostLoginRoute(routeName)) {
           if ($scope.loggedInUser.admin) {
             // Admins can go anywhere.
             useUrl = url;
           }
           else if (! pwgRoutes.isAdminOnlyRoute(routeName)) {
             // Non-admins can go to non-admin segments only.
             useUrl = url;
           }
         }
       }
       else {
         // Ensure that the segment is valid for a non-logged in user.
         if (routeName && pwgRoutes.isPreLoginRoute(routeName))
           useUrl = url;
         else
           useUrl = pwgRoutes.hrefForRouteName('login');
       }

       return useUrl;
     }

     pwgRoutes.redirectToNamedRoute('initializing');

     pwgCheckUser.checkUser().then(
       function(response) {
         // Success
         log.debug(`checkUser: Success. loggedIn=${response.loggedIn}`)
         $scope.initializing = false;
         if (response.loggedIn)
           $scope.setLoggedInUser(response.user);
         else
           $scope.setLoggedInUser(null);

         let useUrl = validateLocationChange($scope.urlOnLoad);
         $location.path(useUrl);
         $scope.routeOnLoad = null;
       },
       function(response) {
         // Failure.
         log.error(response);
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
            let url = routes.controllers.SessionController.logout().url

            let always = () => {
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
       let url = routes.controllers.SessionController.login().url;
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
// Edit Entry Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('EditPasswordEntryCtrl',
  ['$scope', '$injector', function($scope, $injector) {

    var pwgRoutes    = $injector.get('pwgRoutes');
    var pwgLogging   = $injector.get('pwgLogging');
    var $routeParams = $injector.get('$routeParams');
    var pwgAjax      = $injector.get('pwgAjax');
    var pwgFlash     = $injector.get('pwgFlash');

    var log = pwgLogging.logger("EditPasswordEntryCtrl");
    $scope.passwordEntry = null;

    function augmentExtra(extra) {
      extra.deleted = false;
      extra.delete  = () => {
        extra.deleted = true;
        $scope.entryForm.$setDirty();
      }
      return extra;
    }

    var url = routes.controllers.PasswordEntryController.getEntry($routeParams.id).url;
    pwgAjax.get(url,
      function(data) {
        $scope.passwordEntry = data.passwordEntry;
        if ($scope.passwordEntry) {
          if (!$scope.passwordEntry.extras)
            $scope.passwordEntry.extras = [];

          for (let extra of $scope.passwordEntry.extras) {
            augmentExtra(extra);
          }
        }
      },
      function(error) {
        console.log(error);
      }
    );

    // When using the "filter" filter with a function, the specified function
    // is a constructor that returns that actual filter function.
    $scope.fieldNotDeleted = function() {
      return function(extra) { return !extra.deleted; }
    }

    $scope.addExtra = function() {
      $scope.passwordEntry.extras.push(augmentExtra({
        fieldName:  null,
        fieldValue: null,
        id:         null
      }));
    }

    $scope.cancel = function() {
      $scope.validateFormCancellation($scope.entryForm, 'search');
    }

    $scope.save = function() {
      let id = $scope.passwordEntry.id;
      let url = routes.controllers.PasswordEntryController.save(id).url;
      log.debug(`Saving existing entry, name=${$scope.passwordEntry.name}`);
      pwgAjax.post(url, $scope.passwordEntry,
                   function() {
                     pwgFlash.info("Saved.", 5 /* second timeout */)
                     pwgRoutes.redirectToNamedRoute('search');
                   }
      )
    }
  }
]);

// --------------------------------------------------------------------------
// New Entry Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('NewPasswordEntryCtrl',
  ['$scope', '$injector', function($scope, $injector) {

    var pwgRoutes  = $injector.get('pwgRoutes');
    var pwgAjax    = $injector.get('pwgAjax');
    var pwgFlash   = $injector.get('pwgFlash');
    var pwgLogging = $injector.get('pwgLogging');

    var log = pwgLogging.logger('NewPasswordEntryCtrl');

    var createNew = (pw) => {
    }

    $scope.cancel = function() {
      $scope.validateFormCancellation($scope.entryForm, 'search');
    }

    $scope.save = function() {
      let url = routes.controllers.PasswordEntryController.create().url;
      log.debug(`Saving new entry, name=${$scope.passwordEntry.name}`);
      pwgAjax.post(url, $scope.passwordEntry,
                   function() {
                     pwgFlash.info("Saved.", 5 /* second timeout */)
                     pwgRoutes.redirectToNamedRoute('search');
                   }
      )
    }

    $scope.passwordEntry = {
      id:           null,
      name:         "",
      loginID:      "",
      password:     "",
      description:  "",
      url:          "",
      notes:        ""
    }
  }

]);

// --------------------------------------------------------------------------
// Search Controllers
// --------------------------------------------------------------------------

// Outer controller. Multiple copies of the inner controller can be
// instantiated. Only one outer controller will be.

pwGuardApp.controller('SearchCtrl',
  ['$scope', '$injector', function($scope, $injector) {

    var pwgRoutes = $injector.get('pwgRoutes');

    $scope.newEntryURL =  pwgRoutes.hrefForRouteName('new-entry');

  }
]);

pwGuardApp.controller('InnerSearchCtrl',
  ['$scope', '$injector', function($scope, $injector) {

    $scope.searchTerm    = "";
    $scope.searchResults = null;
    $scope.activePanel   = -1; // mobile only
    $scope.sortColumn    = 'name';
    $scope.reverse       = false;

    var pwgAjax        = $injector.get('pwgAjax');
    var pwgFlash       = $injector.get('pwgFlash');
    var pwgTimeout     = $injector.get('pwgTimeout');
    var pwgModal       = $injector.get('pwgModal');
    var pwgLogging     = $injector.get('pwgLogging');
    var pwgRoutes      = $injector.get('pwgRoutes');
    var $cookies       = $injector.get('$cookies');
    var $filter        = $injector.get('$filter');
    var inflector      = $filter('pwgInflector');
    var ellipsize      = $filter('pwgEllipsize');
    var makeUrlPreview = $filter('pwgUrlPreview');

    var SEARCH_ALL_MARKER = '-*-all-*-';
    var originalEntries = {};

    var log = pwgLogging.logger('InnerSearchCtrl');

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
      let url = routes.controllers.PasswordEntryController.searchPasswordEntries().url;
      log.debug(`Issuing search: ${$scope.searchTerm}`);
      pwgAjax.post(url, {searchTerm: $scope.searchTerm}, function(response) {
        $cookies.lastSearch = $scope.searchTerm;
        $scope.searchResults = adjustResults(response.results);
      });
    }

    $scope.showAll = () => {
      $scope.newPasswordEntry = null;
      $scope.searchTerm       = null;
      var url = routes.controllers.PasswordEntryController.all().url;
      pwgAjax.get(url,
        function(response) {
          $cookies.lastSearch = SEARCH_ALL_MARKER;
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
      let lastSearch = $cookies.lastSearch;
      if (lastSearch) {
        if (lastSearch === SEARCH_ALL_MARKER) {
          $scope.showAll();
        }
        else {
          $scope.searchTerm = lastSearch;
          doSearch();
        }
      }

      if (! lastSearch)
        $scope.searchTerm = "";
    }

    var saveEntry = (pw) => {
      let url = routes.controllers.PasswordEntryController.save(pw.id).url;
      pw.password = pw.plaintextPassword;
      pwgAjax.post(url, pw, function(response) {
        pw.editing = false;
        reissueLastSearch();
      });
    }

    var deleteEntry = (pw) => {
      pwgModal.confirm(`Really delete ${pw.name}?`, "Confirm deletion").then(
        function() {
          let url = routes.controllers.PasswordEntryController.delete(pw.id).url;
          pwgAjax.delete(url, {}, reissueLastSearch);
        }
      )
    }

    $scope.newEntry = function() {
      pwgRoutes.redirectToNamedRoute('new-entry');
    }

    $scope.toggleSelectForAll = () => {
      for (var pw of $scope.searchResults) {
        pw.selected = !pw.selected;
      }
    }

    $scope.selectedAny = () => {
      let result = false;
      if ($scope.searchResults) {
        var first = _.find($scope.searchResults, (p) => { return p.selected });
        if (first) result = true;
      }
      return result;
    }

    $scope.editingAny = () => {
      let result = false;
      if ($scope.searchResults) {
        var first = _.find($scope.searchResults, (p) => { return p.editing });
        if (first) result = true;
      }
      return result;
    }

    $scope.deleteSelected = () => {
      if ($scope.searchResults) {
        let toDel = _.filter($scope.searchResults, (p) => { return p.selected });
        let count = toDel.length;
        if (count > 0) {
          let pl = pluralizeCount(count);
          pwgModal.confirm(`You are about to delete ${pl}. Are you sure?`,
                           "Confirm deletion").then(function() {
            let ids = _.map(toDel, (pw) => { return pw.id });
            let url = routes.controllers.PasswordEntryController.deleteMany().url;
            pwgAjax.delete(url, {ids: ids}, function(response) {
              pl = pluralizeCount(response.total);
              pwgFlash.info(`Deleted ${pl}.`);
              reissueLastSearch();
            });
          });
        }
      }
    }

    var cancelEdit = function(form, pw) {
      let doCancel = function() {
        _.extend(pw, originalEntries[pw.id]);
        pw.editing = false;
        reissueLastSearch();
      }

      if (form.$dirty) {
        pwgModal.confirm("You've changed this entry. Really cancel?",
                         "Confirm cancel").then(
          function() {
            doCancel();
          }
        )
      }
      else {
        doCancel();
      }
    }

    var adjustResults = (results) => {
      originalEntries = {};
      let r = _.map(results, (pw) => {
        pw.showPassword          = false;
        pw.editing               = false;
        pw.notesPreview          = ellipsize(pw.notes);
        pw.notesPreviewAvailable = ! (pw.notes === pw.notesPreview);
        pw.showNotesPreview      = pw.notesPreviewAvailable;
        pw.passwordVisible       = false;
        pw.selected              = false;
        pw.showExtras            = false;

        if (pw.url) {
          pw.urlPreview     = makeUrlPreview(pw.url, 20);
          pw.showUrlPreview = true;
        }
        else {
          pw.urlPreview     = null;
          pw.showUrlPreview = false;
        }

        pw.togglePasswordVisibility = () => {
          pw.passwordVisible = !pw.passwordVisible;
        }

        pw.toggleNotesPreview = () => {
          pw.showNotesPreview = !pw.showNotesPreview;
        }

        pw.toggleUrlPreview = () => {
          pw.showUrlPreview = !pw.showUrlPreview;
        }

        originalEntries[pw.id] = pw;

        pw.edit   = function() {
          pwgRoutes.redirectToNamedRoute("edit-entry", {id: pw.id});
        }
        pw.cancel = function(form) { cancelEdit(form, this); }
        pw.save   = function() { saveEntry(this); }
        pw.delete = function() { deleteEntry(this); }
        return pw;
      });
      return r;
    }

    // Initialization.

    reissueLastSearch();
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
      let data = {
        firstName:  $scope.firstName,
        lastName:   $scope.lastName,
        password1:  $scope.password1,
        password2:  $scope.password2
      }

      let url = routes.controllers.UserController.save($scope.loggedInUser.id).url

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
  ['$scope', '$injector', function($scope, $injector) {

    var $timeout   = $injector.get('$timeout');
    var pwgAjax    = $injector.get('pwgAjax');
    var pwgFlash   = $injector.get('pwgFlash');
    var pwgLogging = $injector.get('pwgLogging');

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
      let url = routes.controllers.ImportExportController.importDataUpload().url;
      let data = {
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
        let obj = {
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
        let obj = {
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

      for (let f of $scope.fields) {
        $scope.fields[f.name.toLowerCase()] = f
      }

      for (let h of $scope.headers) {
        let matchedField = $scope.fields[h.name.toLowerCase()];
        if (matchedField) {
          matchedField.matchedTo = h;
          h.matchedTo = matchedField;
        }
      }
    }

    var checkForMatch = () => {
      if ($scope.headers && $scope.filters) {
        let i = _.filter($scope.headers, (h) => { return h.selected });
        let selectedHeader = i[0];
        if (selectedHeader) {
          i = _.filter($scope.fields, (f) => { return f.selected });
          let selectedField = i[0];

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
      let v = ! item.selected;
      for (let other of list) {
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
      let totalRequired = [];
      let matchedRequired = [];

      if ($scope.fields) {
        totalRequired = _.filter($scope.fields, (f) => {
          return f.required
        });
      }

      if ($scope.headers) {
        matchedRequired = _.filter($scope.headers, (h) => {
          let res = false;
          if (h.matchedTo) res = h.matchedTo.required;
          return res;
        });
      }

      return (totalRequired.length === matchedRequired.length);
    }

    $scope.completeImport = () => {
      let data = {
        mappings: {}
      }

      for (let k of $scope.fields) {
        if (k.matchedTo)
          data.mappings[k.name] = k.matchedTo.name;
      }

      let url = routes.controllers.ImportExportController.completeImport().url;
      pwgAjax.post(url, data,
        (response) => {
          $scope.importState = 'complete';
          handleCompletion(response.total);
        },
        (errorData) => {
          // Error already handled.
          $scope.importState = 'new';
        }
      )
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
      $scope.importFilename = null;
      $scope.mimeType       = null;
      $scope.importFile     = null;
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

    var log = pwgLogging.logger('AdminUsersCtrl');

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
      let url = routes.controllers.UserController.save(u.id).url;

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
      let url = routes.controllers.UserController.create().url;

      pwgAjax.post(url, $scope.addingUser, (response) => {
        loadUsers();
        $scope.addingUser = null;
      });
    }

    $scope.editingAny = () => {
      let res = false;
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
      let url = routes.controllers.UserController.getAll().url;

      pwgAjax.get(url, (response) => {
        $scope.users = _.map(response.users, (u) => {
          if ($scope.loggedInUser && (u.id === $scope.loggedInUser.id)) {
            $scope.setLoggedInUser(u); // Update info for current user
          }

          let u2 = _.clone(u);
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
