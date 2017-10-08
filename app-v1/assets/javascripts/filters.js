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

pwgFilters.filter('pwgNewlinesToBRs', ng(function($filter) {
  var NEWLINES = ["\n", "&#10;", "&#x0a;"];

  return function(input) {
    let rep = $filter("pwgReplace");
    let res = input;
    for (let s of NEWLINES) {
      res = rep(res, s, "<br/>", true);
    }

    return res;
  }
}));

// ----------------------------------------------------------------------------
// Shorten a string, adding a ellipsis, if it exceeds a certain length.
// ----------------------------------------------------------------------------

pwgFilters.filter('pwgEllipsize', function() {
  return function(input, max = 30) {
    var res = input;
    if (input) {
      if (typeof max === 'string') {
        max = parseInt(max)
        if (isNaN(max))
          max = 30;
      }

      var trimmed = input.slice(0, max);
      if (input === trimmed)
        res = input;
      else
        res = `${trimmed}...`;
    }

    return res;
  }
});

// ----------------------------------------------------------------------------
// Shorten a URL to a preview.
// ----------------------------------------------------------------------------

pwgFilters.filter('pwgUrlPreview', ng(function($filter) {
  let ellipsize = $filter('pwgEllipsize');
  // Note: The ".*?" construct means "match .*, but non-greedily". This
  // is necessary to prevent the ".*" from capturing trailing "/" characters
  // that we prefer to exclude. See
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions
  let r = new RegExp("^https?://(www.)?(.*?)/*$")

  return function(input, max = 30) {
    let res = input;

    if (input) {
      // First, remove any leading protocol and "www" portion. Note that we
      // only care about HTTP and HTTPS URLs. Anything else is unlikely and
      // untouched.

      let m = r.exec(input)
      if (m) res = m[2];

      res = ellipsize(res, max)
    }

    return res;
  }

}));

/* jshint ignore:end */
