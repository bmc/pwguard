/*
  This code is ES6-based and must be transpiled with traceur. JSHint doesn't
 grok ES6, either, so it must be disabled for this file.

  WARNING: Do NOT include this file in the HTML. sbt-traceur automatically
  mixes it into the main Javascript file.
*/

/* jshint ignore:start */

export function init() {

  if (typeof(String.prototype.endsWith) !== 'function') {
    String.prototype.endsWith = (suffix) => {
      return this.indexOf(suffix, this.length - suffix.length) !== -1;
    }
  }
}

/* jshint ignore:end */

