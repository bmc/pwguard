# Custom Angular.js services

pwgServices = angular.module('pwguard-services', [])

# ----------------------------------------------------------------------------
# Front-end service for AJAX calls. Handles errors in a consistent way, and
# fires up a spinner.
# ----------------------------------------------------------------------------

pwgServices.factory 'pwgAjax', ($http,
                                $rootScope,
                                pwgSpinner,
                                pwgFlash) ->

  # Local
  handleFailure = (data, status, onFailure) ->
    # invoke flash service here
    message = data.error?.message or "Server request failed. We're looking into it."
    pwgFlash.error "(#{status}) #{message}", status
    console.log data
    onFailure(data) if onFailure?

  handleSuccess = (data, status, onSuccess, onFailure) ->
    response =
      data:   data
      status: status

    # Angular doesn't seem to handle 401 responses properly, so we're
    # mimicking them with JSON.

    if data.error?
      if data.status == 401
        response.status = 401

      pwgFlash.error data.error.message if response.data.error.message?
      onFailure(response) if onFailure?
    else
      onSuccess(response)

  http = (config, onSuccess, onFailure)->
    failed = (data, status, headers, config) ->
      pwgSpinner.stop()
      handleFailure data, status, onFailure

    succeeded = (data, status, headers, config) ->
      pwgSpinner.stop()
      handleSuccess data, status, onSuccess, onFailure

    pwgSpinner.start()
    $http(config).success(succeeded).error(failed)

  # Post to a URL.
  #
  # Parameters
  # url       - the URL to which to post
  # data      - the data to send, or null for none.
  # onSuccess - Callback to invoke, with the response, on success
  # onFailure - Optional failure callback, invoked AFTER the regular one.
  post: (url, data, onSuccess, onFailure) ->
    params =
      method: 'POST'
      url:    url
      data:   data
    http(params, onSuccess, onFailure)

  # Get a URL.
  #
  # Parameters:
  #
  # url       - the URL to retrieve
  # onSuccess - Callback to invoke, with the response, on success
  # onFailure - Optional failure callback, invoked AFTER the regular one.
  get: (url, onSuccess, onFailure = null) ->
    params =
      method: 'GET'
      url:    url

    http(params, onSuccess, onFailure)

  # Issue an HTTP DELETE to a URL
  #
  # Parameters:
  #
  # url       - the URL to retrieve
  # onSuccess - Callback to invoke, with the response, on success
  # onFailure - Optional failure callback, invoked AFTER the regular one.
  delete: (url, onSuccess, onFailure = null) ->
    params =
      method: 'DELETE'
      url:    url
    http(params, onSuccess, onFailure)

# ----------------------------------------------------------------------------
# Simple spinner service. Assumes the existence of an element that's monitoring
# the root scope's "showSpinner" variable.
# ----------------------------------------------------------------------------

pwgServices.factory 'pwgSpinner', ($rootScope) ->
  $rootScope.showSpinner = true

  start: ->
    $rootScope.showSpinner = true

  stop: ->
    $rootScope.showSpinner = false

# ----------------------------------------------------------------------------
# Simple flash service. Assumes the existence of an element configured like
# this:
#
#   <div ng-show="flash.info"> {{flash.info}} </div>
#
# This service sets or clears the following variables in the root scope:
#
# flash.message.info    - info alert message
# flash.message.error   - error messages
# flash.message.warning - warning alert messages
#
# The service provides the following functions. These functions are also
# available on the $rootScope.flash object, for use in HTML.
#
# init()         - CALL THIS FIRST at application startup.
# warn(msg)      - issue a warning message (by setting $rootScope.alertWarning)
# info(msg)      - issue an info message
# error(msg)     - issue an error message
# clear(type)    - clear message(s) of the specified type. The types can be:
#                    'warn', 'info', 'error', 'all'
# clearInfo()    - convenience
# clearWarning() - convenience
# clearError()   - convenience
# clearAll()     - convenience
# ----------------------------------------------------------------------------

pwgServices.factory 'pwgFlash', ($rootScope, $timeout) ->

  handleMessage = (type, msg) ->
    m = $rootScope.flash.message
    switch type
      when 'info'    then m.info    = msg
      when 'warning' then m.warning = msg
      when 'error'   then m.error   = msg
      when 'all'
        m.info    = msg
        m.warning = msg
        m.error   = msg

  message = (type, msg) ->
    handleMessage type, msg
    if msg?
      cb = -> handleMessage type, null
      $timeout cb, 5000

  init: ->
    $rootScope.flash =
      message:
        info:    null
        error:   null
        warning: null
      warn:    (msg) ->
        message 'warning', msg
      info:    (msg) ->
        message 'info', msg
      error:   (msg) ->
        message 'error', msg
      clear:   (type) ->
        message type, null
      clearError: ->
        message 'error', null
      clearWarning: ->
        message 'warning', null
      clearInfo: ->
        message 'info', null
      clearAll: ->
        for type in ['info', 'warning', 'error']
          message type, null

  warn: (msg) ->
    $rootScope.flash.warn msg

  error: (msg) ->
    $rootScope.flash.error msg

  info: (msg) ->
    $rootScope.flash.info msg

  clear: (type) ->
    $rootScope.flash.clear type

  clearError: ->
    $rootScope.flash.clearError()

  clearInfo: ->
    $rootScope.flash.clearInfo()

  clearWarning: ->
    $rootScope.flash.clearWarning()

# ----------------------------------------------------------------------------
# Confirmation dialog, and related models
# ----------------------------------------------------------------------------

# RUDIMENTARY! FIX!

pwgServices.factory 'pwgConfirm', ($rootScope) ->

  confirm: (message, callback) ->
    if confirm(message)
      callback(true)
