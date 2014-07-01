# Utility functions shared across the local Angular.js CoffeeScript files.

# Determine the location of an Angular template. Relies on a setting made in
# the index.scala.html file.
window.angularTemplateURL = (filename) ->
  window.ANGULAR_TEMPLATE_URL.replace("TOKEN", filename)
