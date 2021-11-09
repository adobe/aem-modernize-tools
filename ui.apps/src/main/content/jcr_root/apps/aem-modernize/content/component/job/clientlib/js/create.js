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
