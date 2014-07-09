# Angular filters

pwgFilters = angular.module('pwguard-filters', [])

# ----------------------------------------------------------------------------
# Filter to convert boolean values to strings.
# ----------------------------------------------------------------------------

pwgBoolean = ->
  (input) ->
    if input then 'yes' else 'no'

pwgFilters.filter 'pwgBoolean', [pwgBoolean]
