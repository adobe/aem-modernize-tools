(function(document, AemModernize) {
  "use strict";

  let form;

  class CreateStructureJobForm extends AemModernize.CreateJobForm {

    $getRow(data) {
      data.path = data.path || "";
      data.templateRules = data.templateRules || [];
      data.title = data.title ? data.title.replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/&/g, "&amp;") : "";

      const $ruleList = $("<div>");
      $ruleList.addClass("aem-modernize-rule-list aem-modernize-structure-rule-list");
      data.templateRules.forEach((rule) => {
        const $div = $("<div>").addClass("aem-modernize-rule-item");
        let $span = $("<span>").addClass("aem-modernize-rule-title").text(rule.title);
        $div.append($span[0]);
        $span = $("<span>").addClass("aem-modernize-rule-id").attr("data-rule-id", rule.id).text(rule.id);
        $div.append($span[0]);
        $ruleList.append($div[0]);
      });

      const $row = $(
        '<tr is="coral-table-row" itemprop="item" class="foundation-collection-item" data-foundation-collection-item-id="' + data.path + '">' +
        '<td is="coral-table-cell" class="select" alignment="center" coral-table-rowselect>' +
        '<coral-checkbox></coral-checkbox>' +
        '</td>' +
        '<td is="coral-table-cell" class="foundation-collection-item-title" alignment="column" value="' + data.title + '">' +
        '<span>' + data.title + '</span><div class="foundation-layout-util-subtletext">' + data.path + '</div>' +
        '</td>' +
        '<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-template-rule-count" alignment="center">' +
        '<span>' + data.templateRules.length + '</span>' +
        '</td>' +
        '<td is="coral-table-cell" alignment="center">' +
        '<coral-icon icon="gears" size="S" autoarialable="on" role="img" aria-label="gears"></coral-icon>' +
        '</td>' +
        '</tr>'
      );

      $row.find(".aem-modernize-template-rule-count").append($ruleList[0]);
      return $row;
    }

    populateItem(item = {}) {
      return this.checkPagePermissions(item)
        .then(this.getPageData)
        .then(this.getRules);
    }

    getRules = (item) => {
      return new Promise((resolve, reject) => {
        const params = {
          path: item.path + "/jcr:content",
          reprocess: $("input[name='reprocess']").is(":checked")
        }
        const url = this.$getForm().data("aemModernizeListRulesUrl");
        $.ajax({
          url: url,
          method: "POST",
          data: params,
          success: (data) => {
            item.templateRules = data.rules;
            resolve(item);
          }, error: (xhr, status, error) => {
            reject(item);
          }
        });
      });
    }

    addHidden = (item) => {
      const $wizard = this.$getWizard();
      if ($wizard.find("input[type='hidden'][name='path'][value='" + item.path + "']").length === 0) {
        const $hidden = $('<input type="hidden">').attr("name", "path").attr("data-path", item.path).attr("value", item.path);
        $wizard.append($hidden);
      }

      item.templateRules.forEach((rule) => {
        if ($wizard.find("input[type='hidden'][name='templateRule'][value='" + rule.id + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "templateRule").attr("value", rule.id);
          $wizard.append($hidden);
        }
      });
    }

    removeHidden($row) {
      const $wizard = this.$getWizard();
      const id = $row.data("foundationCollectionItemId");
      // Remove row from Wizard and hidden input
      for (let itemIdx = 0; itemIdx < $wizard.pageList.length; itemIdx++) {
        if ($wizard.pageList[itemIdx].path === id) {
          $wizard.pageList.splice(itemIdx, 1);
          break;
        }
      }
      const $templateRule = $wizard.find("input[type='hidden'][name='templateRule']");
      if ($templateRule.length !== 0) {
        $templateRule.each((idx, element) => {
          if ($wizard.find("span.aem-modernize-rule-id[data-rule-id='" + $(element).val() + "']").length === 0) {
            $(element).remove();
          }
        });
      }
    }

    checkPermissionPromises() {
      const promises = [];
      promises.push(this.#checkTargetPermissions());
      return promises;
    }

    getFormData($form) {
      const data = super.getFormData($form);
      data.paths = [].concat.apply([], $("input[type='hidden'][name='path']").map((idx, item) => {
        return item.value;
      }));
      data.templateRules = [].concat.apply([], $("input[type='hidden'][name='templateRule']").map((idx, item) => {
        return item.value;
      }));
      data.targetPath = $("input[name='targetPath']").val();
      data.reprocess = $("input[name='reprocess']").is(":checked");
      return data;
    }

    #checkTargetPermissions = () => {
      return new Promise((resolve, reject) => {
        const target = $("input[name='targetPath']").val();
        if (target === '') {
          resolve();
        } else {
          const url = Granite.HTTP.externalize(conf + ".permissions.json");
          $.getJSON(url, {"privileges": "rep:write"}, (data) => {
            if (data.hasOwnProperty("rep:write") && data["rep:write"]) {
              resolve();
            } else {
              reject();
            }
          }).fail(() => {
            reject()
          });
        }
      })
    }
  }

  $(function() {
    form = new CreateStructureJobForm();
  });

})(document, AemModernize);
