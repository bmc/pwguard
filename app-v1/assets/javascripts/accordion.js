/*
 ******************************************************************************
 Accordion component (service and directive)

 This code is ES6-based and must be transpiled with traceur. JSHint doesn't
 grok ES6, either, so it must be disabled for this file.

 WARNING: Do NOT include this file in the HTML. traceur automatically
 mixes it into the main Javascript file.
 ******************************************************************************
 */
var pwgAccordionModule = angular.module('pwguard-accordion', []);

var templateURL   = window.angularTemplateURL;

// ----------------------------------------------------------------------------
// Accordion directives. Currently implemented via Bootstrap.
//
// Usage:
//
// <pwg-accordion allow-multiselect="truthy|falsy">
//   <pwg-accordion-item ...> ...
// </pwg-accordion>
//
// allow-multiselect: If this expression evaluates truthy, then multiple
//                    items can be open at once. Otherwise, only one can
//                    be open at a time; opening one closes the others.
//                    REQUIRED.
// id:                ID for the accordion element. REQUIRED.
// ----------------------------------------------------------------------------

pwgAccordionModule.directive("pwgAccordion", ng(function() {
  return {
    restrict:    'EA',
    replace:     false,
    transclude:  true,
    templateUrl: templateURL('directives/pwgAccordion.html'),

    scope: {
      allowMultiselect: '&',
      id:               '@'
    },

    controller:  function($scope, $element, $attrs) {
      let self = this;

      this.children = [];

      // Child accordion items must call this function to register themselves.
      //
      // Parameters:
      //
      // hide - function to hide the element, for use if multiselection is off
      //
      // Returns
      //   An object containing the following fields:
      //
      //   id          - the item's assigned (numeric) id
      //   accordionID - the ID of the parent accordion
      this.registerItem = function(hide) {
        let id = self.children.length;
        self.children.push({
          id:      id,
          hide:    hide
        });

        return {id : id, accordionID: $attrs.id};
      }

      // Child accordion items must call this function when they are opened.
      this.itemShown = (id) => {
        if (! $scope.allowMultiselect()) {
          // Multiselect isn't permitted, so we have to close whatever else
          // is open.
          for (var i = 0; i < self.children.length; i++){
            let child = self.children[i];
            if (i !== id)
              child.hide();
          }
        }
      }
    },

    link: function ($scope, element, attrs) {
      if (! attrs.id)
        throw new Error("pwg-accordion: id attribute is required")
      if (! attrs.allowMultiselect)
        throw new Error("pwg-accordion: allow-multiselect attribute is required")
    }
  }
}));

pwgAccordionModule.directive("pwgAccordionItem", ng(function() {
  return {
    restrict:   'EA',
    replace:    false,
    require:    '^pwgAccordion',
    transclude:  true,
    templateUrl: templateURL('directives/pwgAccordionItem.html'),

    scope: {
      title: '@'
    },

    link: function($scope, element, attr, ctrl) {
      let panel = element.find('.panel').first();
      let collapsible = element.find('.panel-collapse').first();

      function hide()  { collapsible.collapse('hide'); }
      function show()  { collapsible.collapse('show'); }
      function shown() { return collapsible.hasClass('in'); }

      $scope.toggle = function() {
        if (shown())
          hide();
        else
          show();
      }

      ctrl.registerItem(hide);
    }
  }
}));
