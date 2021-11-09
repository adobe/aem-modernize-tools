(function(document, AemModernize) {
  "use strict";

  let form;

  class CreateFullJobForm extends AemModernize.CreateJobForm {}

  $(function() {
    form = new CreateFullJobForm();
  });

})(document, AemModernize);
