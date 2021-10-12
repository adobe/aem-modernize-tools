(function(document, AemModernize) {
  "use strict";

  let form;

  class CreateComponentJobForm extends AemModernize.CreateJobForm {

    $getRow(data) {
      data.path = data.path || "";
      data.componentRules = data.componentRules || [];
      data.title = data.title ? data.title.replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/&/g, "&amp;") : "";

      const $list = $('<div>');
      $list.addClass("aem-modernize-rule-list aem-modernize-component-rule-list");
      data.componentRules.forEach((rule) => {
        const $div = $('<div>').addClass("aem-modernize-rule-item");
        let $span = $('<span>').addClass("aem-modernize-rule-title").text(rule.title);
        $div.append($span);
        $span = $('<span>').addClass("aem-modernize-rule-id").attr("data-rule-id", rule.path).text(rule.path);
        $div.append($span);
        $list.append($div);
      });

      const $row = $(
        '<tr is="coral-table-row" itemprop="item" class="foundation-collection-item" data-foundation-collection-item-id="' + data.path + '">' +
          '<td is="coral-table-cell" class="select" alignment="center">' +
            '<coral-checkbox coral-table-rowselect></coral-checkbox>' +
          '</td>' +
          '<td is="coral-table-cell" class="foundation-collection-item-title" alignment="column" value="' + data.title + '">' +
            '<span>' + data.title + '</span><div class="foundation-layout-util-subtletext">' + data.path + '</div>' +
          '</td>' +
          '<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-component-rule-count" alignment="center">' +
            '<span>' + data.componentRules.length + '</span>' +
          '</td>' +
          '<td is="coral-table-cell" alignment="center">' +
            '<coral-icon icon="gears" size="S" autoarialable="on" role="img" aria-label="gears"></coral-icon>' +
          '</td>' +
        '</tr>'
      );

      $row.find(".aem-modernize-component-rule-count").append($list[0]);
      return $row;
    }

    addHidden(item) {
      super.addHidden(item);
      const $wizard = this.$getWizard();
      item.componentRules.forEach((rule) => {
        if ($wizard.find("input[type='hidden'][name='componentRule'][value='" + rule.path + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "componentRule").attr("data-path", rule.path).attr("value", rule.path);
          $wizard.append($hidden);
        }
      });
    }

    removeHidden($row) {
      super.removeHidden($row);
      const $wizard = this.$getWizard();
      const $componentRules = $wizard.find("input[type='hidden'][name='componentRule']");
      if ($componentRules.length !== 0) {
        $componentRules.each((idx, element) => {
          if ($wizard.find("span.aem-modernize-rule-id[data-rule-id='" + $(element).val() + "']").length === 0) {
            $(element).remove();
          }
        });
      }
    }
  }

  $(function() {
    form = new CreateComponentJobForm();
  });

})(document, AemModernize);
