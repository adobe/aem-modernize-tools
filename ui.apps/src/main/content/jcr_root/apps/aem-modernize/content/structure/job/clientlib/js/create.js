(function(document, AemModernize) {
  "use strict";

  let form;

  class CreateStructureJobForm extends AemModernize.CreateJobForm {}

  $(function() {
    form = new CreateStructureJobForm();
  });

})(document, AemModernize);
