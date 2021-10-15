(function(document, Granite, $) {
  "use strict";
  $(document).on("coral-collection:add coral-collection:remove", ".foundation-collection", function() {

    const $collection = $(this);
    $(".aem-modernize-collectionstatus").filter(function() {
      return $collection.is($(this).data("aemModernizeTarget"));
    }).each(function() {
      let count = Math.max($collection[0].items.length, $("form.aem-modernize-job-form input[type=hidden][name=path]").length) - $collection.find(".empty-row").length;
      if (count === 0) {
        count = "0";
      }
      const content = Granite.I18n.get("{0} page(s)", count, "The current selection count");
      $(this).html(content);
    });

  });
})(document, Granite, Granite.$);
