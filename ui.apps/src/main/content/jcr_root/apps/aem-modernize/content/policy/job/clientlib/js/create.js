(function(document, AemModernize) {
  "use strict";

  let form;

  class CreatePolicyJobForm extends AemModernize.CreateJobForm {

    addPathHidden(item) {
      const $wizard = this.$getWizard();
      item.policyPaths.forEach((path) => {
        if ($wizard.find("input[type='hidden'][name='path'][value='" + path + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "path").attr("data-path", path).attr("value", path);
          $wizard.append($hidden);
        }
      });
    }

    removePathHidden($row) {
      const $policyPaths = $row.find('.aem-modernize-policy-path');
      if ($policyPaths.length !== 0) {
        $policyPaths.each((idx, element) => {
          $("input[type='hidden'][name='path'][value='" + $(element).data("pathId") + "']").remove();
        });
      }
    }
  }

  $(function() {
    form = new CreatePolicyJobForm();

  });

})(document, AemModernize);
