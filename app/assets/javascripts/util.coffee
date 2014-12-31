# Miscellaneous utilities.
# ----------------------------------------------------------------------------

# Add endsWith() to String, if it isn't already there.

if typeof(String.prototype.endsWith) isnt 'function'
  String.prototype.endsWith = (suffix) ->
    this.indexOf(suffix, this.length - suffix.length) isnt -1
