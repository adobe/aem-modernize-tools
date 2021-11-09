/*-
 * #%L
 * AEM Modernize Tools - UI apps
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
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
      const content = Granite.I18n.get("{0} conversions(s)", count, "The current selection count");
      $(this).html(content);
    });

  });
})(document, Granite, Granite.$);
