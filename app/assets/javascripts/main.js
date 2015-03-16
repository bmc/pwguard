/*
  This code is ES6-based and must be transpiled with traceur. JSHint doesn't
  grok ES6, either, so it must be disabled for this file.
*/

/* jshint ignore:start */

import * as util from './util';
import * as filters from './filters';
import * as services from './services';
import * as directives from './directives';
import * as pwgModal from './modal';
import * as pwgFlash from './flash';
import * as pwgAccordion from './accordion';

util.init()


var pwGuardApp = angular.module("PWGuardApp", ['ngRoute',
                                               'ngCookies',
                                               'ngSanitize',
                                               'pwguard-accordion',
                                               'pwguard-flash',
                                               'pwguard-modal',
                                               'pwguard-spinner',
                                               'pwguard-services',
                                               'pwguard-filters',
                                               'pwguard-directives']);

// ##########################################################################
// Utility functions
// ##########################################################################

// ##########################################################################
// Initialization logic
// ##########################################################################

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

pwGuardApp.config(ng(function($routeProvider, $provide) {

  var templateURL = angularTemplateURL;

  function checkUser($injector) {
    let $q        = $injector.get('$q');
    let pwgUser   = $injector.get('pwgUser');
    let pwgRoutes = $injector.get('pwgRoutes');
    let deferred = $q.defer();

    let user = pwgUser.currentUser();
    if (user !== null) {
      deferred.resolve(user);
    }

    else {
      pwgUser.checkUser().then(
        function(response) {
          if (response.loggedIn) {
            deferred.resolve(response.user);
          }
          else {
            let currentRoute = pwgRoutes.currentRouteName();
            let isPreLoginRoute = pwgRoutes.isPreLoginRoute(currentRoute);
            if (! isPreLoginRoute)
              pwgRoutes.redirectToNamedRoute('login');
            deferred.resolve(null);
          }
        },
        function(response) {
          pwgRoutes.redirectToNamedRoute('login');
          deferred.reject(response);
        }
      );
    }

    return deferred.promise;
  }

  $routeProvider.
    when("/login", {
      templateUrl: templateURL("login.html"),
      controller:  'LoginCtrl',
      name:        'login',
      admin:       false,
      postLogin:   false,
      preLogin:    true,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    when("/search", {
      templateUrl:      templateURL("search.html"),
      controller:       'SearchCtrl',
      name:             'search',
      admin:            false,
      postLogin:        true,
      preLogin:         false,
      allowQueryString: true,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    when("/edit/:id", {
      templateUrl: templateURL("edit-password-entry.html"),
      controller:  'EditPasswordEntryCtrl',
      name:        'edit-entry',
      admin:       false,
      postLogin:   true,
      preLogin:    false,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    when("/new-entry/:fromID?", {
      templateUrl: templateURL("new-password-entry.html"),
      controller:  'NewPasswordEntryCtrl',
      name:        'new-entry',
      admin:       false,
      postLogin:   true,
      preLogin:    false,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    when("/profile", {
      templateUrl: templateURL("profile.html"),
      controller:  'ProfileCtrl',
      name:        'profile',
      admin:       false,
      postLogin:   true,
      preLogin:    false,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    when("/admin/users", {
      templateUrl: templateURL("admin-users.html"),
      controller:  'AdminUsersCtrl',
      name:        'admin-users',
      admin:       true,
      postLogin:   true,
      preLogin:    false,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    when("/import-export", {
      templateUrl: templateURL("ImportExport.html"),
      controller:  'ImportExportCtrl',
      name:        'import-export',
      admin:       false,
      postLogin:   true,
      preLogin:    false,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    when("/about", {
      templateUrl: templateURL("about.html"),
      controller:  'AboutCtrl',
      name:        'about',
      admin:       false,
      postLogin:   true,
      preLogin:    true,
      resolve: {
        currentUser: ($injector) => {
          return checkUser($injector);
        }
      }
    }).
    otherwise({
      redirectTo:  "/search",
      defaultName: "search",
      isDefault:   true
    });

  checkMissingFeatures();
}));

pwGuardApp.run(ng(function($rootScope, $injector) {

  var pwgLogging = $injector.get('pwgLogging');
  var pwgRoutes  = $injector.get('pwgRoutes');
  var $location  = $injector.get('$location');

  var log = pwgLogging.logger('rootScope');

  $rootScope.hrefForRouteName = function(name) {
    return pwgRoutes.hrefForRouteName(name);
  }

  $rootScope.$on('$routeChangeSuccess', function(e) {
    var route = pwgRoutes.routeForURL($location.path());
    if (route) {
      if (! route.allowQueryString) {
        // If there's something already in the query string (from a previous
        // search, for instance), AngularJS will not remove the query string
        // when the user moves to a different route. That's probably because,
        // with "#" routes, the URL is actually the same document, and
        // AngularJS's route module doesn't mess with query strings at all.
        // So, since the browser thinks it's the same route, it leaves the
        // query string intact. Since, to this application, the routes really
        // are different, if the route is defined as not supporting a query
        // string (see the routing table), clear the query string manually.
        $location.search({});
      }
    }
  });

}));

// ##########################################################################
// Controllers
// ##########################################################################

// --------------------------------------------------------------------------
// Main Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('MainCtrl', ng(function($scope, $injector) {

  var $rootScope         = $injector.get('$rootScope');
  var $location          = $injector.get('$location');
  var pwgTimeout         = $injector.get('pwgTimeout');
  var pwgAjax            = $injector.get('pwgAjax');
  var pwgFlash           = $injector.get('pwgFlash');
  var pwgLogging         = $injector.get('pwgLogging');
  var pwgRoutes          = $injector.get('pwgRoutes');
  var angularTemplateURL = $injector.get('angularTemplateURL');
  var pwgError           = $injector.get('pwgError');
  var pwgModal           = $injector.get('pwgModal');
  var pwgUser            = $injector.get('pwgUser');

  // Put the template URL in the scope, because it's used inside templates
  // (e.g., within ng-include directives).

  $scope.templateURL = angularTemplateURL;
  $scope.version     = window.version;
  $scope.gitVersion  = window.gitVersion;

  $scope.debugMessages = [];
  $scope.debug = (msg) => {
    $scope.debugMessages.push(msg);
  }

  var log = pwgLogging.logger("MainCtrl");

  $scope.dialogConfirmTitle    = null;
  $scope.dialogConfirmMessage  = null;
  $scope.urlOnLoad             = $location.path();
  $scope.initializing          = true;
  $scope.flashAfterRouteChange = null;
  $scope.isMobile              = window.browserIsMobile;
  $scope.defaultHref           = pwgRoutes.defaultRouteHref();

  $scope.routeIsActive = pwgRoutes.routeIsActive;

  $scope.currentUser = pwgUser.currentUser;
  $scope.userIsAdmin = pwgUser.userIsAdmin;
  $scope.isLoggedIn  = pwgUser.isLoggedIn;

  $scope.showImportExport = () => {
    return (! $scope.isMobile) && pwgUser.isLoggedIn();
  }

  $scope.showUserAdmin = () => {
    return (! $scope.isMobile) && $scope.userIsAdmin();
  }

  pwgAjax.on401(function() {
    if (pwgUser.isLoggedIn()) {
      pwgUser.setLoggedInUser(null);
      $scope.flashAfterRouteChange = "Session timeout. Please log in again.";
    }

    pwgRoutes.redirectToNamedRoute('login');
  });
}));

// --------------------------------------------------------------------------
// Navbar Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('NavbarCtrl', ng(function($scope, $injector) {

  var pwgAjax   = $injector.get('pwgAjax');
  var pwgModal  = $injector.get('pwgModal');
  var pwgRoutes = $injector.get('pwgRoutes');
  var pwgUser   = $injector.get('pwgUser');

  $scope.logout = () => {

    pwgModal.confirm("Really log out?", "Confirm logout").then(
      function() {
        if (pwgUser.isLoggedIn()) {
          let url = routes.controllers.SessionController.logout().url

          let always = () => {
            pwgUser.setLoggedInUser(null);
            pwgRoutes.redirectToNamedRoute('login');
          }

          pwgAjax.post(url, {}).then(
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
}));

// --------------------------------------------------------------------------
// Login Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('LoginCtrl',
  ng(function($scope, $injector, currentUser, pwgCheckRoute) {

    pwgCheckRoute('login', currentUser);

    var pwgAjax       = $injector.get('pwgAjax');
    var pwgFlash      = $injector.get('pwgFlash');
    var pwgLogging    = $injector.get('pwgLogging');
    var pwgRoutes     = $injector.get('pwgRoutes');
    var pwgUser       = $injector.get('pwgUser');
    var pwgSearchTerm = $injector.get('pwgSearchTerm');

    var log = pwgLogging.logger('LoginCtrl');

    $scope.email            = null;
    $scope.password         = null;
    $scope.rememberTime     = 10;
    $scope.rememberUserDays = null;

    $scope.clear = () => {
      $scope.email    = null;
      $scope.password = null;
    }

    $scope.login = () => {
      let url = routes.controllers.SessionController.login().url;
      let rememberTime = null;
      let days = parseInt($scope.rememberUserDays);
      if (! isNaN(days))
        rememberTime = moment.duration(days, "days").asMilliseconds();

      let data = {
        email:        $scope.email,
        password:     $scope.password,
        rememberTime: rememberTime
      }

      pwgAjax.post(url, data).then(

        // Success.
        function(response) {
          pwgUser.setLoggedInUser(response.data.user);
          log.debug("Login successful");
          pwgSearchTerm.clearSavedTerm();
          pwgRoutes.redirectToDefaultRoute();
        },

        // Failure
        function(response) {
          log.error(response.data);
          // Nothing to do. Error was handled by pwgAjax
        }
      );
    }

  }
));

// --------------------------------------------------------------------------
// Edit Entry Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('EditPasswordEntryCtrl', ng(
  function($scope, $injector, currentUser) {

    var pwgCheckRoute = $injector.get('pwgCheckRoute');

    pwgCheckRoute('edit-entry', currentUser);

    var pwgLogging    = $injector.get('pwgLogging');
    var $routeParams  = $injector.get('$routeParams');
    var pwgAjax       = $injector.get('pwgAjax');

    var log = pwgLogging.logger("EditPasswordEntryCtrl");

    var id = $routeParams.id;

    $scope.passwordEntry = null;
    $scope.saveURL = routes.controllers.PasswordEntryController.save(id).url;

    var url = routes.controllers.PasswordEntryController.getEntry($routeParams.id).url;
    pwgAjax.get(url).then(
      function(response) {
        $scope.passwordEntry = response.data.passwordEntry;
        log.debug(`Editing: ${JSON.stringify($scope.passwordEntry)}`);
      },
      function(errorResponse) {
        log.error(JSON.stringify(errorResponse.data));
      }
    );
  }
));

// --------------------------------------------------------------------------
// New Entry Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('NewPasswordEntryCtrl', ng(
  function($scope, $injector, currentUser) {

    var pwgCheckRoute = $injector.get('pwgCheckRoute');

    pwgCheckRoute('new-entry', currentUser)

    var pwgAjax       = $injector.get('pwgAjax');
    var pwgLogging    = $injector.get('pwgLogging');
    var $routeParams  = $injector.get('$routeParams');
    var pwgRoutes     = $injector.get('pwgRoutes');
    var pwgForm       = $injector.get('pwgForm');

    var log = pwgLogging.logger('NewPasswordEntryCtrl');

    // If an ID came in on the URL, then this new entry is supposed to be
    // a copy of an existing entry. In that case, we add "Copy of" to the
    // name and mark it immediately dirty. Otherwise, we just present a new,
    // empty screen.
    var copyFrom = $routeParams.fromID;
    if (copyFrom) {
      // Mark the form dirty when it comes up, since we're generating content
      // automatically.
      $scope.dirtyOnLoad = true;
      let url = routes.controllers.PasswordEntryController.getEntry(copyFrom).url;
      pwgAjax.get(url).then(
        function(response) {
          var data = response.data;
          data.passwordEntry.name = `Copy of ${data.passwordEntry.name}`;
          $scope.passwordEntry    = data.passwordEntry;
        },
        function(error) {
          pwgRoutes.redirectToDefaultRoute();
        }
      );
    }

    else {
      $scope.dirtyOnLoad = false;
      $scope.passwordEntry = {
        id:                null,
        name:              "",
        loginID:           "",
        password:          "",
        encryptedPassword: "",
        keywords:          [],
        description:       "",
        url:               "",
        notes:             "",
        extras:            [],
        securityQuestions: []
      }
    }

    $scope.saveURL = routes.controllers.PasswordEntryController.create().url;
  }
));

// --------------------------------------------------------------------------
// Search Controllers
// --------------------------------------------------------------------------

// Outer controller. Multiple copies of the inner controller can be
// instantiated. Only one outer controller will be.

pwGuardApp.controller('SearchCtrl', ng(function($scope, $injector, currentUser) {

  var pwgAjax        = $injector.get('pwgAjax');
  var pwgCheckRoute  = $injector.get('pwgCheckRoute');
  var pwgFlash       = $injector.get('pwgFlash');
  var pwgTimeout     = $injector.get('pwgTimeout');
  var pwgModal       = $injector.get('pwgModal');
  var pwgLogging     = $injector.get('pwgLogging');
  var pwgRoutes      = $injector.get('pwgRoutes');
  var pwgSearchTerm  = $injector.get('pwgSearchTerm');
  var $filter        = $injector.get('$filter');
  var $location      = $injector.get('$location');
  var inflector      = $filter('pwgInflector');
  var ellipsize      = $filter('pwgEllipsize');
  var makeUrlPreview = $filter('pwgUrlPreview');

  pwgCheckRoute('search', currentUser);

  let url =  routes.controllers.PasswordEntryController.getTotal().url;
  pwgAjax.get(url).then((response) => {
    $scope.totalPasswords = response.data.total;
  });

  $scope.newEntryURL   =  pwgRoutes.hrefForRouteName('new-entry', {'fromID': ""});
  $scope.searchTerm    = "";
  $scope.searchResults = null;
  $scope.activePanel   = -1; // mobile only
  $scope.sortColumn    = 'name';
  $scope.reverse       = false;

  var SEARCH_ALL_MARKER = '-*-all-*-';
  var originalEntries = {};

  var log = pwgLogging.logger('SearchCtrl');

  var pluralizeCount = (count) => {
    return inflector(count, "entry", "entries");
  }

  var validSearchTerm = () => {
    if ($scope.searchTerm)
      return $scope.searchTerm.length >= 2;
    else
      return false;
  }

  $scope.pluralizeResults = (n) => { return pluralizeCount(n); }

  var clearResults = () => {
    originalEntries = {};
    $scope.searchResults = null;
  }

  $scope.issueSearch = () => { doSearch(); }

  $scope.mobileSelect = (i) => {
    $(`#results-${i}`).select();
  }

  function saveSearchTerm(term) {
    pwgSearchTerm.saveSearchTerm(term);
    $location.search('q', term);
     }

  function getTermFromURL() {
    return $location.search()['q'];
  }

  var doSearch = () => {
    originalEntries = {};
    $scope.newPasswordEntry = null;
    let url = routes.controllers.PasswordEntryController.searchPasswordEntries().url;
    log.debug(`Issuing search: ${$scope.searchTerm}`);
    pwgAjax.post(url, {searchTerm: $scope.searchTerm}).then(function(response) {
      // Save the search term, and put it in the URL for bookmarking.
      saveSearchTerm($scope.searchTerm);
      $scope.searchResults = processResults($scope.searchTerm,
                                            response.data.results);
      log.debug("search results:", JSON.stringify($scope.searchResults));
    });
  }

  $scope.showAll = () => {
    $scope.newPasswordEntry = null;
    $scope.searchTerm       = null;
    var url = routes.controllers.PasswordEntryController.getAllForUser().url;
    pwgAjax.get(url).then(function(response) {
      saveSearchTerm(SEARCH_ALL_MARKER);
      $scope.searchResults = processResults(SEARCH_ALL_MARKER,
                                            response.data.results);
    });
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
    let lastSearch = getTermFromURL();
    if (! lastSearch)
      lastSearch = pwgSearchTerm.getSavedTerm();

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
    pwgAjax.post(url, pw).then(function(response) {
      pw.editing = false;
      reissueLastSearch();
    });
  }

  var deleteEntry = (pw) => {
    pwgModal.confirm(`Really delete ${pw.name}?`, "Confirm deletion").then(
      function() {
        let url = routes.controllers.PasswordEntryController.delete(pw.id).url;
        pwgAjax.delete(url, {}).then(function(response) {
          reissueLastSearch()
        });
      }
    )
  }

  $scope.newEntry = function() {
    pwgRoutes.redirectToNamedRoute('new-entry', {'fromID': ""});
  }

  $scope.toggleSelectForAll = () => {
    for (var pw of $scope.searchResults.entries) {
      pw.selected = !pw.selected;
    }
  }

  $scope.selectedAny = () => {
    let result = false;
    if ($scope.searchResults) {
      var first = _.find($scope.searchResults.entries,
                         (p) => { return p.selected });
      if (first) result = true;
    }
    return result;
  }

  $scope.editingAny = () => {
    let result = false;
    if ($scope.searchResults) {
      var first = _.find($scope.searchResults.entries,
                         (p) => { return p.editing });
      if (first) result = true;
    }
    return result;
  }

  $scope.deleteSelected = () => {
    if ($scope.searchResults) {
      let toDel = _.filter($scope.searchResults.entries,
                           (p) => { return p.selected });
      let count = toDel.length;
      if (count > 0) {
        let pl = pluralizeCount(count);
        pwgModal.confirm(`You are about to delete ${pl}. Are you sure?`,
                         "Confirm deletion").then(function() {
          let ids = _.map(toDel, (pw) => { return pw.id });
          let url = routes.controllers.PasswordEntryController.deleteMany().url;
          pwgAjax.delete(url, {ids: ids}).then(function(response) {
            pl = pluralizeCount(response.data.total);
            pwgFlash.info(`Deleted ${pl}.`);
            reissueLastSearch();
          });
        });
      }
    }
  }

  var cancelEdit = function(form, pw) {
    let doCancel = function() {
      _.assign(pw, originalEntries[pw.id]);
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

  var processResults = (term, results) => {
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
      pw.editURL               = pwgRoutes.hrefForRouteName('edit-entry',
                                                            {'id': pw.id});
      pw.copyURL               = pwgRoutes.hrefForRouteName('new-entry',
                                                            {'fromID': pw.id});
      pw.hasExtraFields        = (pw.extras.length > 0) ||
                                 (pw.securityQuestions.length > 0);

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

      pw.cancel = function(form) { cancelEdit(form, this); }
      pw.save   = function() { saveEntry(this); }
      pw.delete = function() { deleteEntry(this); }
      return pw;
    });

    return {
      entries:    r,
      searchTerm: term
    }
  }

  // Initialization.

  reissueLastSearch();
}));

// --------------------------------------------------------------------------
// Profile Controllers
// --------------------------------------------------------------------------

pwGuardApp.controller('ProfileCtrl',
  ng(function($scope, $injector, currentUser, pwgCheckRoute) {

    pwgCheckRoute('profile', currentUser);

    var pwgLogging = $injector.get('pwgLogging');
    var pwgAjax    = $injector.get('pwgAjax');
    var pwgFlash   = $injector.get('pwgFlash');
    var pwgUser    = $injector.get('pwgUser');
    var pwgRoutes  = $injector.get('pwgRoutes');

    var log = pwgLogging.logger('ProfileCtrl');

    if (currentUser === null) {
      console.log("ERROR: Not logged in.");
      pwgRoutes.redirectToNamedRoute('login');
    }

    $scope.email     = currentUser.email;
    $scope.firstName = currentUser.firstName;
    $scope.lastName  = currentUser.lastName;

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

      let url = routes.controllers.UserController.save(currentUser.id).url

      pwgAjax.post(url, data).then(function(response) {
        log.debug("Save complete.");
        pwgUser.setLoggedInUser(response.data);
        pwgFlash.info("Saved.");
        form.$setPristine();
      });
    }
  }
));

// --------------------------------------------------------------------------
// Import/Export Controllers
// --------------------------------------------------------------------------

pwGuardApp.controller('ImportExportCtrl',
  ng(function($scope, $injector, currentUser, pwgCheckRoute) {

    pwgCheckRoute('import-export', currentUser);

    var pwgTimeout = $injector.get('pwgTimeout');
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
      pwgTimeout.timeout(3000, function() { $scope.downloading = false; });
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

    $scope.uploadPercent = 0;

    $scope.upload = () => {
      let url = routes.controllers.ImportExportController.importDataUpload().url;
      let data = {
        filename: $scope.importFilename,
        contents: $scope.importFile,
        mimeType: $scope.mimeType
      }

      $scope.uploading = true;

      pwgAjax.postWithProgress(url, data).then(
        function(response) {
          // Use a timeout, to give the progress bar a chance to register.
          $scope.uploadPercent = 100;
          pwgTimeout.timeout(1000 /* 2 seconds */, function() {
            $scope.importState = 'mapping';
            prepareMappingData(response.data);
            $scope.uploading = false;
          });
        },
        function(error) {
          $scope.uploading = false;
          $scope.uploadPercent = 0;
        },
        function(percentCompleted) {
          log.debug(`Progress notification: ${percentCompleted}%`);
          $scope.uploadPercent = percentCompleted;
        }
      );
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
      if ($scope.headers && $scope.fields) {
        let i = _.filter($scope.headers, (h) => { return h.selected });
        let selectedHeader = i[0];
        log.debug(`checkForMatch: selectedHeader=${JSON.stringify(selectedHeader)}`);
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
      pwgAjax.post(url, data).then(
        (response) => {
          $scope.importState = 'complete';
          handleCompletion(response.data.total);
        },
        (errorResponse) => {
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
  }
));

// --------------------------------------------------------------------------
// Admin Users Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('AdminUsersCtrl',
  ng(function($scope, $injector, currentUser, pwgCheckRoute) {

    pwgCheckRoute('admin-users', currentUser);

    var pwgAjax    = $injector.get('pwgAjax');
    var pwgFlash   = $injector.get('pwgFlash');
    var pwgLogging = $injector.get('pwgLogging');
    var pwgModal   = $injector.get('pwgModal');
    var pwgUser    = $injector.get('pwgUser');

    var log = pwgLogging.logger('AdminUsersCtrl');

    $scope.users          = null;
    $scope.addingUser     = null;
    $scope.sortColumn     = "email";
    $scope.reverse        = false;

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

      pwgAjax.post(url, u).then((response) => {
        originalUsers[u.email] = _.omit(u, 'save', 'cancel', 'edit', 'editing');
        u.editing = false;
        loadUsers();
      });
    }

    var cancelEdit = (u) => {
      _.extend(u, originalUsers[u.email]);
      u.editing = false;
    }

    var deleteUser = (u) => {
      if (u.id === currentUser.id)
        pwgFlash.error("You can't delete yourself!");
      else {
        pwgModal.confirm(`Really delete ${u.email}?`, "Confirm deletion").then(
          function() {
            var url = routes.controllers.UserController.delete(u.id).url
            pwgAjax.delete(url, {}).then((response) => { loadUsers() });
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

      pwgAjax.post(url, $scope.addingUser).then((response) => {
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
      let url = routes.controllers.UserController.getAllWithTotalPasswords().url;
      pwgAjax.get(url).then((response) => {
        $scope.users = _.map(response.data.users, (u) => {
          if (currentUser && (u.id === currentUser.id)) {
            pwgUser.setLoggedInUser(u); // Update info for current user
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
  }
));


// --------------------------------------------------------------------------
// About Controller
// --------------------------------------------------------------------------

pwGuardApp.controller('AboutCtrl',
  ng(function($scope, $injector, currentUser, pwgCheckRoute) {

    var pwgLogging = $injector.get('pwgLogging');

    var log = pwgLogging.logger('AboutCtrl');

    log.debug(`Entry. currentUser=${JSON.stringify(currentUser)}`);
    pwgCheckRoute('about', currentUser);
  })
);


/* jshint ignore:end */
