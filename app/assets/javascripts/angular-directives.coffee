# Local Angular directives.

pwgDirectives = angular.module 'pwguard-directives', ['pwguard-services',
                                                      'ngAnimate']
templateURL   = window.angularTemplateURL

# -----------------------------------------------------------------------------
# Flash block
# -----------------------------------------------------------------------------

pwgFlash = ($compile, $rootScope) ->
  restrict:    'EA'
  transclude:  false
  templateUrl: templateURL('directives/pwgFlash.html')
  replace:     true
  scope:
    flashType: "@"

  link: (scope, element, attrs) ->
    scope.message = null

    animations = []
    if attrs.animateShow?
      animations.push "show: 'fade-show'"
    if attrs.animateHide?
      animations.push "hide: 'fade-hide'"

    if animations.length > 0
      element.attr 'ng-animate', "{#{animations.join(',')}}"

    $rootScope.$watch "flash.message.#{scope.flashType}", (newValue) ->
      scope.message = newValue

    scope.clear = ->
      scope.message = null

pwgDirectives.directive 'pwgFlash', ['$compile',
                                     '$rootScope',
                                     pwgFlash]

# -----------------------------------------------------------------------------
# Drop down menu
# -----------------------------------------------------------------------------

menuNextID = 0

# Simple drop-down menu, no cascading.

dropdownMenu = ->
  # Based on http://tympanus.net/codrops/2012/10/04/custom-drop-down-list-styling/

  DropDown = (element) ->
    this.dd          = element
    this.placeholder = this.dd.children 'span'
    this.options     = this.dd.find 'ul.dropdown-menu > li'
    this.val         = ''
    this.index       = -1
    this.initEvents()

  DropDown.prototype =
    initEvents: ->
      obj = this
      this.dd.on 'click', (event) ->
        $(this).toggleClass 'active'
        false

      obj.options.on 'click', ->
        opt       = $(this)
        obj.val   = opt.text()
        obj.index = opt.index()

    getValue: ->
      this.val

    getIndex: ->
      this.index

  nextID = 1

  # The result object

  restrict:    'EA'
  transclude:  true
  replace:     true
  template:    """
               <div class="dropdown-menu-wrapper">
                 <span class="dropdown-menu-label">{{label}}</span>
                 <ul class="dropdown-menu" ng-transclude>
                 </ul>
               </div>
               """
  scope:
    id:          "@"
    label:       "@"
    placeholder: "@"

  controller: ($scope, $element, $attrs) ->
    this

  link: (scope, element, attrs) ->
    scope.id = "dropdown-#{nextID}" unless scope.id?

    nextID += 1
    id = scope.id

    wrapper = $("#" + id)

    dd = new DropDown(wrapper)
    $(document).click ->
      wrapper.removeClass 'active'

pwgDirectives.directive 'dropdownMenu', [dropdownMenu]

dropdownMenuItem = ->
  restrict:   'EA'
  transclude: true
  replace:    true
  template:   """
              <li ng-transclude></li>
              """

  require:    "^dropdownMenu"

  link: (scope, element, attrs, controller) ->
    return

pwgDirectives.directive 'dropdownMenuItem', [dropdownMenuItem]
