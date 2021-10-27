(function(document, AemModernize) {
  "use strict";

  let form;

  class CreateComponentJobForm extends AemModernize.CreateJobForm {

    $getRow(data) {
      data.path = data.path || "";
      data.componentPaths = data.componentPaths || [];
      data.componentRules = data.componentRules || [];
      data.title = data.title ? data.title.replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/&/g, "&amp;") : "";

      const $pathList = $("<div>");
      $pathList.addClass("aem-modernize-path-list aem-modernize-component-path-list");
      data.componentPaths.forEach((path) => {
        const $div = $("<div>").addClass("aem-modernize-path-item");
        const $span = $("<span>").addClass("aem-modernize-component-path").attr("data-path-id", path).text(path);
        $div.append($span[0]);
        $pathList.append($div[0]);
      });

      const $ruleList = $("<div>");
      $ruleList.addClass("aem-modernize-rule-list aem-modernize-component-rule-list");
      data.componentRules.forEach((rule) => {
        const $div = $("<div>").addClass("aem-modernize-rule-item");
        let $span = $("<span>").addClass("aem-modernize-rule-title").text(rule.title);
        $div.append($span[0]);
        $span = $("<span>").addClass("aem-modernize-rule-id").attr("data-rule-id", rule.path).text(rule.path);
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
          '<td is="coral-table-cell" class="aem-modernize-path-count aem-modernize-component-path-count" alignment="center">' +
            '<span>' + data.componentPaths.length + '</span>' +
          '</td>' +
          '<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-component-rule-count" alignment="center">' +
            '<span>' + data.componentRules.length + '</span>' +
          '</td>' +
          '<td is="coral-table-cell" alignment="center">' +
            '<coral-icon icon="gears" size="S" autoarialable="on" role="img" aria-label="gears"></coral-icon>' +
          '</td>' +
        '</tr>'
      );

      $row.find(".aem-modernize-component-path-count").append($pathList[0]);
      $row.find(".aem-modernize-component-rule-count").append($ruleList[0]);
      return $row;
    }

    populateItem(item = {}) {
      return new Promise((resolve, reject) => {
        const url = this.$getForm().data("aemModernizeListComponentsUrl");
        if (item.path) {
          $.getJSON(url, {path: item.path}, (data) => {
            item.componentPaths = data.paths;
            resolve(item);
          }).fail(() => {
            reject(item)
          });
        } else {
          item.componentPaths = [];
          resolve(item);
        }
      }).then(this.checkPagePermissions)
        .then(this.getPageData)
        .then(this.getRules);
    }

    getRules = (item) => {
      return new Promise((resolve, reject) => {
        const params = {
          path: item.componentPaths
        }
        const url = this.$getForm().data("aemModernizeListRulesUrl");
        $.ajax({
          url: url,
          method: "POST",
          data: params,
          success: (data) => {
            item.componentRules = data.rules;
            resolve(item);
          }, error: (xhr, status, error) => {
            reject(item);
          }
        });
      });
    }

    addHidden = (item) => {
      const $wizard = this.$getWizard();
      item.componentPaths.forEach((path) => {
        if ($wizard.find("input[type='hidden'][name='path'][value='" + path + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "path").attr("data-path", path).attr("value", path);
          $wizard.append($hidden);
        }
      });

      item.componentRules.forEach((rule) => {
        if ($wizard.find("input[type='hidden'][name='componentRule'][value='" + rule.path + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "componentRule").attr("data-path", rule.path).attr("value", rule.path);
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

      const $componentPaths = $row.find('aem-modernize-component-path');
      if ($componentPaths.length !== 0) {
        $componentPaths.each((idx, element) => {
          $("input[type='hidden'][name='path'][value='" + $(element).data("pathId") + "']").remove();
        });
      }

      const $componentRules = $wizard.find("input[type='hidden'][name='componentRule']");
      if ($componentRules.length !== 0) {
        $componentRules.each((idx, element) => {
          if ($wizard.find("span.aem-modernize-rule-id[data-rule-id='" + $(element).val() + "']").length === 0) {
            $(element).remove();
          }
        });
      }
    }

    getFormData($form) {
      const data = super.getFormData($form);
      data.paths = [].concat.apply([], $("input[type='hidden'][name='path']").map((idx, item) => {
        return item.value;
      }));
      data.componentRules = [].concat.apply([], $("input[type='hidden'][name='componentRule']").map((idx, item) => {
        return item.value;
      }));
      return data;
    }
  }

  $(function() {
    form = new CreateComponentJobForm();
  });

})(document, AemModernize);
