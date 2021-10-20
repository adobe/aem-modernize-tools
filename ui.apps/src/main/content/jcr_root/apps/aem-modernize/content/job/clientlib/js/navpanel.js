(function(document, Granite, $) {
  "use strict";

  $(document).on("coral-tablist:change.aem-modernize-navpanel-bucket-action", function(e) {
    const href = e.target.selectedItem.dataset.href;
    if (href) {
      window.location = Granite.HTTP.externalize(href);
    }
  });
})(document, Granite, Granite.$);
