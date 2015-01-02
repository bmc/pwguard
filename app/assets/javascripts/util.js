/* jshint ignore:start */

/*
  This code is ES6-based and must be transpiled with traceur.

  WARNING: Do NOT include this file in the HTML. sbt-traceur automatically
  mixes it into the main Javascript file.
*/

export function init() {

  if (typeof(String.prototype.endsWith) !== 'function') {
    String.prototype.endsWith = (suffix) => {
      return this.indexOf(suffix, this.length - suffix.length) !== -1;
    }
  }
}

export function ellipsize(input, max = 30) {
  var res = input;
  if (input) {
    if (typeof max === 'string') {}
    max = parseInt(max)
    if (isNaN(max))
      max = 30;

    var trimmed = input.slice(0, max);
    if (input === trimmed)
      res = input;
    else
      res = `${trimmed}...`;
  }

  return res;
}



/* jshint ignore:end */

