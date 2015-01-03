/*
  Custom AngularJS filters.

  This code is ES6-based and must be transpiled with traceur. JSHint doesn't
  grok ES6, either, so it must be disabled for this file.

  WARNING: Do NOT include this file in the HTML. sbt-traceur automatically
  mixes it into the main Javascript file.
 */

/* jshint ignore:start */

var pwgFilters = angular.module('pwguard-filters', []);

// ----------------------------------------------------------------------------
// Extremely simple pluralization/singularization filter.
// ----------------------------------------------------------------------------

pwgFilters.filter('pwgInflector', function() {
  return function(count, singular, plural) {
    var n = 0;

    if (typeof count === 'number')
      n = count
    else if (typeof count === 'string') {
      n = parseInt(count)
      if (isNaN(n)) n = 0
    }

    var res;
    switch(n) {
      case 0:
        res = `no ${plural}`
        break
      case 1:
        res = `one ${singular}`
        break
      default:
        res = `${n} ${plural}`
        break
    }

    return res;
  }
});

// ----------------------------------------------------------------------------
// Filter to convert boolean values to strings.
// ----------------------------------------------------------------------------

pwgFilters.filter('pwgBoolean', function() {
  return function(input) {
    if (input) {
      return "yes";
    }
    else {
      return "no";
    }
  }
});

// ----------------------------------------------------------------------------
// Filter to do in-string replacement
// ----------------------------------------------------------------------------

pwgFilters.filter('pwgReplace', function() {
  return function(input, fromString, toString, all) {
    var res = input;
    if (input && (typeof input === 'string')) {
      res = input.replace(fromString, toString);
      if (all) {
        var prev = input;
        while (prev !== res) {
          prev = res;
          res = res.replace(fromString, toString);
        }
      }
    }
    return res;
  }
});

// ----------------------------------------------------------------------------
// Converted embedded newlines to <br/>
// ----------------------------------------------------------------------------

pwgFilters.filter('pwgNewlinesToBRs', ['$filter', function($filter) {
  var NEWLINES = ["\n", "&#10;", "&#x0a;"];

  return function(input) {
    let rep = $filter("pwgReplace");
    let res = input;
    for (let s of NEWLINES) {
      res = rep(res, s, "<br/>", true);
    }

    return res;
  }
}]);

/* jshint ignore:end */
