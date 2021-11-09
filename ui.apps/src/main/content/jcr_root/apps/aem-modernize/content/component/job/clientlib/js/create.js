(function(document, AemModernize) {
  "use strict";

  let form;

  class CreateComponentJobForm extends AemModernize.CreateJobForm {

    addPathHidden(item) {
      const $wizard = this.$getWizard();
      item.componentPaths.forEach((path) => {
        if ($wizard.find("input[type='hidden'][name='path'][value='" + path + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "path").attr("data-path", path).attr("value", path);
          $wizard.append($hidden);
        }
      });
    }

    removePathHidden($row) {
      const $componentPaths = $row.find('.aem-modernize-component-path');
      if ($componentPaths.length !== 0) {
        $componentPaths.each((idx, element) => {
          $("input[type='hidden'][name='path'][value='" + $(element).data("pathId") + "']").remove();
        });
      }
    }

  }

  $(function() {
    form = new CreateComponentJobForm();
  });

})(document, AemModernize);
