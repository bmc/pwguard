# Angular filters

pwgFilters = angular.module('pwguard-filters', [])

# ----------------------------------------------------------------------------
# Filter to convert boolean values to strings.
# ----------------------------------------------------------------------------

pwgBoolean = ->
  (input) ->
    if input then 'yes' else 'no'

pwgFilters.filter 'pwgBoolean', [pwgBoolean]

# ----------------------------------------------------------------------------
# Filter to do in-string replacement
# ----------------------------------------------------------------------------

pwgReplace = ->
  (input, from, to, all) ->
    if input
      res = input.replace(from, to)
      if all
        prev = input
        while prev isnt res
          prev = res
          res = res.replace(from, to)
      res
    else
      input

pwgFilters.filter 'pwgReplace', [pwgReplace]

