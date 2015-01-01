# Angular filters

pwgFilters = angular.module('pwguard-filters', [])

# ----------------------------------------------------------------------------
# Extremely simple pluralization/singularization filter.
# ----------------------------------------------------------------------------

pwgInflector = ->
  (count, singular, plural) ->
    n = if typeof count is 'number'
          count
        else if typeof count is 'string'
          c = parseInt count
          c = 0 if isNaN(c)
          c
        else
          0
    switch n
      when 0 then "no #{plural}"
      when 1 then "one #{singular}"
      else "#{n} #{plural}"

pwgFilters.filter 'pwgInflector', [pwgInflector]

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

