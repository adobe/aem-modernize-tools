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
(function(document, Granite, $, AemModernize) {
  "use strict";

  class CreateJobForm {

    static NO_CONTENT = Granite.I18n.get("There are no items.");
    static EMPTY_ROW = '<tr is="coral-table-row" class="empty-row"><td is="coral-table-cell" alignment="center">' + CreateJobForm.NO_CONTENT + '</td></tr>';

    #ui;
    #$form;
    #$wizard;
    #$table;
    #columnCount;
    operation

    constructor() {
      this.#ui = $(window).adaptTo("foundation-ui");
      this.#$form = $(".aem-modernize-job-form");
      this.#$wizard = this.#$form.find(".aem-modernize-job-wizard");
      this.#$table = this.#$wizard.find(".aem-modernize-job-pages");
      this.#$wizard.pageList = [];
      this.operation = this.#$form.data("aem-modernize-operation");
      this.#columnCount = this.#$table.data('emptyTableColumnSpan')
      this.#setup()
    }

    $getWizard() {
      return this.#$wizard;
    }

    addPathHidden(item) {
      const $wizard = this.$getWizard();
      if ($wizard.find("input[type='hidden'][name='path'][value='" + item.path + "']").length === 0) {
        const $hidden = $('<input type="hidden">').attr("name", "path").attr("data-path", item.path).attr("value", item.path);
        $wizard.append($hidden);
      }
    }

    removePathHidden($row) {
      const $wizard = this.$getWizard();
      const id = $row.data("foundationCollectionItemId");
      // Remove row from Wizard and hidden input
      for (let itemIdx = 0; itemIdx < $wizard.pageList.length; itemIdx++) {
        if ($wizard.pageList[itemIdx].path === id) {
          $wizard.pageList.splice(itemIdx, 1);
          break;
        }
      }
      $wizard.find("input[type='hidden'][name='path'][value='" + id + "']").remove();
    }

    // Private Methods

    #populateItem = (item = {}) => {
      return this.#getPageData(item)
        .then(this.#checkPagePermissions)
        .then(this.#checkDesignPermissions)
        .then(this.#getRules)
    }

    #getPageData = (item) => {
      const path = Granite.HTTP.getPath(window.location.href);
      return new Promise((resolve, reject) => {
        const params = {
          path: item.path,
          reprocess: $("input[name='reprocess']").is(":checked")
        }
        const url = Granite.HTTP.externalize(path + ".pagedata.json");
        $.getJSON(url, params, (data) => {
          item.title = data["jcr:title"];
          item.designPath = data['cq:designPath'];
          resolve(item);
        }).fail(() => {
          reject(item);
        });
      });
    }

    #checkPagePermissions = (item) => {
      return new Promise((resolve, reject) => {
        const url = Granite.HTTP.externalize(item.path + ".permissions.json");
        $.getJSON(url, {"privileges": "rep:write"}, (data) => {
          if (data.hasOwnProperty("rep:write") && data["rep:write"]) {
            item.hasPermission = true;
            resolve(item);
          } else {
            item.hasPermission = false;
            reject(item);
          }
        }).fail(() => {
          reject(item)
        });
      });
    }

    #checkDesignPermissions = (item) => {
      if ($("input[name='confPath']").length > 0 && item.designPath) {
        return new Promise((resolve, reject) => {
          const url = Granite.HTTP.externalize(item.designPath + ".permissions.json");
          $.getJSON(url, {"privileges": "rep:write"}, (data) => {
            if (data.hasOwnProperty("rep:write") && data["rep:write"]) {
              item.hasPermission = true;
              resolve(item);
            } else {
              item.hasPermission = false;
              reject(item);
            }
          }).fail(() => {
            reject(item)
          });
        });
      } else {
        return new Promise((resolve) => resolve(item));
      }
    }

    #getRules = (item) => {
      const promises = []
      promises.push(this.#listComponentRules(item));
      promises.push(this.#listDesignRules(item));
      promises.push(this.#listStructureRules(item));
      return Promise.all(promises)
        .then((all) => {
          return new Promise((resolve) => {
            const item = {...all[0], ...all[1], ...all[2]};
            resolve(item);
          });
        });

    }

    #listComponentRules = (item) => {
      if (this.#$form.data("aemModernizeComponents") === true && item.path) {
        const path = Granite.HTTP.getPath(window.location.href);
        return new Promise((resolve, reject) => {
          const params = {
            path: item.path,
            reprocess: $("input[name='reprocess']").is(":checked")
          }
          const url = Granite.HTTP.externalize(path + ".listcomponents.json");
          $.getJSON(url, params, (data) => {
            item.componentPaths = data.paths;
            resolve(item);
          }).fail(() => {
            reject(item)
          });
        }).then((item) => {
          if (item.componentPaths && item.componentPaths.length > 0) {
            const url = Granite.HTTP.externalize(path + ".listrules.component.json");
            const params = {
              path: item.componentPaths
            }
            return new Promise((resolve, reject) => {
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
          } else {
            item.componentRules = [];
            return new Promise((resolve) => resolve(item));
          }
        })
      } else {
        item.componentPaths = [];
        item.componentRules = [];
        return new Promise((resolve) => resolve(item));
      }
    }

    #listDesignRules = (item) => {

      if (this.#$form.data("aemModernizeDesigns") === true && item.path) {
        const include = this.#$form.find("input[name='includeSuperTypes']").is(":checked");
        const path = Granite.HTTP.getPath(window.location.href);
        return new Promise((resolve, reject) => {
          const url = Granite.HTTP.externalize(path + ".listdesigns.json");
          $.getJSON(url, {path: item.path, includeSuperTypes: include}, (data) => {
            item.policyPaths = data.paths;
            resolve(item);
          }).fail(() => {
            reject(item)
          });
        }).then((item) => {
          if (item.policyPaths && item.policyPaths.length > 0) {
            const url = Granite.HTTP.externalize(path + ".listrules.policy.json");
            const params = {
              path: item.policyPaths
            }
            return new Promise((resolve, reject) => {
              $.ajax({
                url: url,
                method: "POST",
                data: params,
                success: (data) => {
                  item.policyRules = data.rules;
                  resolve(item);
                }, error: (xhr, status, error) => {
                  reject(item);
                }
              });
            });
          } else {
            item.policyRules = [];
            return new Promise((resolve) => resolve(item));
          }
        })
      } else {
        item.policyPaths = [];
        item.policyRules = [];
        return new Promise((resolve) => resolve(item));
      }
    }

    #listStructureRules = (item) => {
      if (this.#$form.data("aemModernizeStructure") === true && item.path) {
        const params = {
          path: item.path + "/jcr:content",
          reprocess: $("input[name='pageHandling'][value='RESTORE']").is(":checked")
        }
        const url = Granite.HTTP.externalize(Granite.HTTP.getPath(window.location.href) + ".listrules.template.json");
        return new Promise((resolve, reject) => {
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
      } else {
        item.templateRules = [];
        return new Promise((resolve) => resolve(item));
      }
    }

    #checkConfPermissions = () => {
      if ($("input[name='confPath']").length > 0) {
        return new Promise((resolve, reject) => {
          const conf = $("input[name='confPath']").val();
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
        });
      } else {
        return new Promise(resolve => resolve());
      }
    }

    #checkTargetPermissions = () => {
      if ($("input[name='targetRoot']").length > 0) {
        return new Promise((resolve, reject) => {
          const target = $("input[name='targetRoot']").val();
          if (target === '') {
            resolve();
          } else {
            const url = Granite.HTTP.externalize(target + ".permissions.json");
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
      } else {
        return new Promise(resolve => resolve());
      }
    }

    #getItem = (path) => {
      let retVal = undefined;
      for (let index = 0; index < this.#$wizard.pageList.length; index++) {
        if (this.#$wizard.pageList[index].path === path) {
          retVal = this.#$wizard.pageList[index];
          break;
        }
      }
      return retVal;
    }

    #addTableRow = (item) => {
      this.#$table[0].items.add(this.#getRow(item)[0])
    }

    #getRow = (data) => {
      data.title = data.title ? data.title.replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/&/g, "&amp;") : "";

      const $row = $('<tr is="coral-table-row" itemprop="item" class="foundation-collection-item" data-foundation-collection-item-id="' + data.path + '">');
      let $cell = $('<td is="coral-table-cell" class="select" alignment="center" coral-table-rowselect>');
      $cell.append('<coral-checkbox></coral-checkbox>');
      $row.append($cell[0]);

      $cell =
        $('<td is="coral-table-cell" class="foundation-collection-item-title" alignment="column" value="' + data.title + '">');
      $cell.append('<span>' + data.title + '</span><div class="foundation-layout-util-subtletext">' + data.path + '</div>');
      $row.append($cell[0]);

      if (this.#$form.data("aemModernizeStructure") === true) {
        let $ruleList = $('<div class="aem-modernize-rule-list aem-modernize-structure-rule-list">');
        data.templateRules.forEach((rule) => {
          const $div = $('<div class="aem-modernize-rule-item">');
          let $span = $("<span>").addClass("aem-modernize-rule-title").text(rule.title);
          $div.append($span[0]);
          $span = $("<span>").addClass("aem-modernize-rule-id").attr("data-rule-id", rule.id).text(rule.id);
          $div.append($span[0]);
          $ruleList.append($div[0]);
        });
        $cell =
          $('<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-template-rule-count" alignment="center">');
        $cell.append('<span>' + data.templateRules.length + '</span>');
        $cell.append($ruleList[0]);
        $row.append($cell[0]);
      }

      if (this.#$form.data("aemModernizeDesigns") === true) {
        let $ruleList = $('<div class="aem-modernize-rule-list aem-modernize-policy-path-list">');
        data.policyPaths.forEach((path) => {
          const $div = $('<div class="aem-modernize-path-item">');
          let $span = $("<span>").addClass("aem-modernize-policy-path").attr("data-path-id", path).text(path);
          $div.append($span[0]);
          $ruleList.append($div[0]);
        });
        $cell =
          $('<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-policy-path-count" alignment="center">');
        $cell.append('<span>' + data.policyPaths.length + '</span>');
        $cell.append($ruleList[0]);
        $row.append($cell[0]);

        $ruleList = $('<div class="aem-modernize-rule-list aem-modernize-policy-rule-list">');
        data.policyRules.forEach((rule) => {
          const $div = $('<div class="aem-modernize-rule-item">');
          let $span = $("<span>").addClass("aem-modernize-rule-title").text(rule.title);
          $div.append($span[0]);
          $span = $("<span>").addClass("aem-modernize-rule-id").attr("data-rule-id", rule.id).text(rule.id);
          $div.append($span[0]);
          $ruleList.append($div[0]);
        });
        $cell =
          $('<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-policy-rule-count" alignment="center">');
        $cell.append('<span>' + data.policyRules.length + '</span>');
        $cell.append($ruleList[0]);
        $row.append($cell[0]);
      }

      if (this.#$form.data("aemModernizeComponents") === true) {
        let $ruleList = $('<div class="aem-modernize-rule-list aem-modernize-component-rule-list">');
        data.componentPaths.forEach((path) => {
          const $div = $('<div class="aem-modernize-rule-item">');
          let $span = $("<span>").addClass("aem-modernize-component-path").attr("data-path-id", path).text(path);
          $div.append($span[0]);
          $ruleList.append($div[0]);
        });
        $cell =
          $('<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-component-path-count" alignment="center">');
        $cell.append('<span>' + data.componentPaths.length + '</span>');
        $cell.append($ruleList[0]);
        $row.append($cell[0]);

        $ruleList = $('<div class="aem-modernize-rule-list aem-modernize-component-rule-list">');
        data.componentRules.forEach((rule) => {
          const $div = $('<div class="aem-modernize-rule-item">');
          let $span = $("<span>").addClass("aem-modernize-rule-title").text(rule.title);
          $div.append($span[0]);
          $span = $("<span>").addClass("aem-modernize-rule-id").attr("data-rule-id", rule.id).text(rule.id);
          $div.append($span[0]);
          $ruleList.append($div[0]);
        });
        $cell =
          $('<td is="coral-table-cell" class="aem-modernize-rule-count aem-modernize-component-rule-count" alignment="center">');
        $cell.append('<span>' + data.componentRules.length + '</span>');
        $cell.append($ruleList[0]);
        $row.append($cell[0]);
      }

      // const $details = $('<td is="coral-table-cell" alignment="center">');
      // $details.append('<coral-icon icon="gears" size="S" autoarialable="on" role="img" aria-label="gears"></coral-icon>');
      // $row.append($details[0]);

      return $row;
    }

    #addHidden = (item) => {
      const $wizard = this.$getWizard();
      $wizard.pageList.splice($wizard.pageList.length, 0, item);
      this.addPathHidden(item);

      item.templateRules.forEach((rule) => {
        if ($wizard.find("input[type='hidden'][name='templateRule'][value='" + rule.id + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "templateRule").attr("value", rule.id);
          $wizard.append($hidden);
        }
      });

      item.policyRules.forEach((rule) => {
        if ($wizard.find("input[type='hidden'][name='policyRule'][value='" + rule.id + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "policyRule").attr("value", rule.id);
          $wizard.append($hidden);
        }
      });

      item.componentRules.forEach((rule) => {
        if ($wizard.find("input[type='hidden'][name='componentRule'][value='" + rule.id + "']").length === 0) {
          const $hidden = $('<input type="hidden">').attr("name", "componentRule").attr("value", rule.id);
          $wizard.append($hidden);
        }
      });
      this.#$table.trigger("coral-collection:add");
    }

    #removeHidden = ($row) => {
      const $wizard = this.$getWizard();
      const id = $row.data("foundationCollectionItemId");
      // Remove row from Wizard and hidden input
      for (let itemIdx = 0; itemIdx < $wizard.pageList.length; itemIdx++) {
        if ($wizard.pageList[itemIdx].path === id) {
          $wizard.pageList.splice(itemIdx, 1);
          break;
        }
      }
      this.removePathHidden($row);

      const $templateRule = this.#$wizard.find("input[type='hidden'][name='templateRule']");
      if ($templateRule.length !== 0) {
        $templateRule.each((idx, element) => {
          if ($wizard.find("span.aem-modernize-rule-id[data-rule-id='" + $(element).val() + "']").length === 0) {
            $(element).remove();
          }
        });
      }

      const $policyRule = this.#$wizard.find("input[type='hidden'][name='policyRule']");
      if ($policyRule.length !== 0) {
        $policyRule.each((idx, element) => {
          if ($wizard.find("span.aem-modernize-rule-id[data-rule-id='" + $(element).val() + "']").length === 0) {
            $(element).remove();
          }
        });
      }

      const $componentRules = this.#$wizard.find("input[type='hidden'][name='componentRule']");
      if ($componentRules.length !== 0) {
        $componentRules.each((idx, element) => {
          if ($wizard.find("span.aem-modernize-rule-id[data-rule-id='" + $(element).val() + "']").length === 0) {
            $(element).remove();
          }
        });
      }
      this.#$table.trigger("coral-collection:remove");
    }

    #remove = () => {
      const selected = this.#$table[0].selectedItems;
      if (selected != null && selected.length > 0) {
        for (let selectedIdx = 0; selectedIdx < selected.length; selectedIdx++) {
          const $current = $(selected[selectedIdx]);
          this.#$table[0].items.remove($current[0])
          this.#removeHidden($current);
        }
        const paginator = this.#$table.data("foundation-layout-table.internal.paginator");
        let more = paginator.hasNext;
        if (more === undefined) {
          more = selected.length >= (paginator.offset + paginator.limit);
        }
        if (this.#$table[0].items.getAll().length === 0) {
          paginator.restart(0, more);
        } else {
          paginator.restart(paginator.offset - selected.length, more);
        }
        this.#refreshPageList();
      }
    }

    #addChildren = (pages) => {
      if (pages.length === 0) {
        return;
      }
      const paginator = this.#$table.data("foundation-layout-table.internal.paginator");
      const offset = paginator.offset;
      const promises = [];
      let showNotice = false;

      pages.forEach((path) => {
        if (path !== "/content") {
          const item = this.#getItem(path);
          if (!item) {
            promises.push(this.#populateItem({path: path}).then((item) => {
              return new Promise((resolve) => {
                this.#addTableRow(item);
                this.#addHidden(item)
                paginator.offset = this.#$table[0].items.getAll().length;
                resolve(item);
              });
            }));
          } else {
            showNotice = true;
          }
        }
      });

      Promise.all(promises)
        .then(this.#refreshPageList)
        .catch(this.#showError)
        .finally(() => {
          this.#ui.clearWait();
          if (showNotice) {
            this.#ui.notify(Granite.I18n.get("Error"), Granite.I18n.get("Selected page already exist in the list."), "notice");
          }
        });
    }

    #updateNext = () => {
      const activeStep = this.#$wizard.find(".foundation-wizard-step-active");

      let valid = true;

      const fields = activeStep.adaptTo("foundation-validation-helper").getSubmittables();
      fields.forEach((field) => {
        const $field = $(field);
        if (!$field.is(":visible")) {
          return;
        }

        const api = $field.adaptTo("foundation-field");
        const validation = $field.adaptTo("foundation-validation");
        if (api && api.isDisabled()) {
          if (validation) {
            validation.checkValidity();
            validation.updateUI();
          }
          return;
        } else if ($field.is(":disabled")) {
          return;
        }

        if (validation) {
          if (!validation.checkValidity()) {
            valid = false;
          }
          validation.updateUI();
        }
      });
      activeStep.data("foundation-wizard-step.internal.valid", valid);
      this.#$wizard.adaptTo("foundation-wizard").toggleNext(valid);
    }

    #refreshPageList = () => {
      this.#$table[0].trigger("foundation-selections-change");
      const next = this.#$wizard.find(".aem-modernize-job-create-next")[0];
      if (this.#$wizard.pageList.length === 0) {
        this.#$table[0].items.add($(CreateJobForm.EMPTY_ROW)[0]);
        $(".empty-row td").attr("colspan", this.#columnCount);
        this.#$table.trigger("coral-collection:add");
        next.disabled = true;
      } else {
        const $empty = this.#$table.find(".empty-row");
        if ($empty.length !== 0) {
          this.#$table[0].items.remove($empty[0]);
          next.disabled = false;
        }
      }
    }

    #showError = (item) => {
      if (item && item.path && item.hasPermission !== true) {
        this.#ui.prompt(
          Granite.I18n.get("Error"),
          Granite.I18n.get("You do not have permission to convert that content."),
          "error",
          [{id: "no-permissions", text: Granite.I18n.get("Ok")}]
        );
      } else {
        throw item;
      }
    }

    #paginate = () => {
      const _this = this;
      const $scrollContainer = this.#$table.children("[coral-table-scroll]");

      const Paginator = $(window).adaptTo("foundation-util-paginator");
      const paginator = new Paginator({
        el: $scrollContainer[0],
        limit: 30,
        wait: (paginator) => {
          _this.#ui.wait();
          return {
            clear: () => {
              _this.#ui.clearWait();
            }
          }
        },
        resolveURL: (paginator) => {
          return "";
        },
        processResponse: (paginator, html) => {
          return new Promise((resolve, reject) => {
            const items = _this.#$wizard.pageList.slice(paginator.offset, paginator.offset + paginator.limit);
            items.forEach((item) => {
              _this.#addTableRow(item);
            });
            const more = this.#$wizard.pageList.length > (paginator.offset + paginator.limit);
            resolve({length: items.length, hasNext: more});
          })
        }
      });

      this.#$table.data("foundation-layout-table.internal.paginator", paginator);
      Coral.commons.ready(this.#$table[0], () => {
        const offset = this.#$table.find(".foundation-collection-item").length;
        paginator.start(offset, false);
      });
    }

    #getFormData() {
      const data = {};
      data.name = this.#$form.find("input[name='name']")[0].value;
      data.type = this.operation;
      data.paths = [].concat.apply([], $("input[type='hidden'][name='path']").map((idx, item) => {
        return item.value;
      }));
      data.templateRules = [].concat.apply([], $("input[type='hidden'][name='templateRule']").map((idx, item) => {
        return item.value;
      }));
      data.policyRules = [].concat.apply([], $("input[type='hidden'][name='policyRule']").map((idx, item) => {
        return item.value;
      }));
      data.componentRules = [].concat.apply([], $("input[type='hidden'][name='componentRule']").map((idx, item) => {
        return item.value;
      }));
      data.confPath = $("input[name='confPath']").val();
      data.overwrite = $("input[name='overwrite']").is(":checked");
      data.sourceRoot = $("input[name='sourceRoot']").val();
      data.targetRoot = $("input[name='targetRoot']").val();
      data.pageHandling = $("input[name='pageHandling']:checked").val();
      return data;
    }

    #scheduleJob = (data) => {
      const _this = this;
      _this.#ui.wait();

      const url = this.#$form.data("aemModernizeUrl");
      $.ajax({
        url: url,
        method: "POST",
        data: {data: JSON.stringify(data)},
        dataType: "json",
        success: (json) => {
          const messenger = $(window).adaptTo("foundation-util-messenger");
          messenger.put(null, json.message, "success");
          _this.#ui.clearWait();
          location.href = Granite.HTTP.externalize(this.#$form.data("aemModernizeJobViewUrl") + json.job);
        },
        error: (xhr, status, error) => {
          _this.#ui.clearWait();
          const json = xhr.responseJSON;
          let message = json && json.message || error;
          _this.#ui.notify(Granite.I18n.get("Error"), Granite.I18n.get(message), "error");
        }
      });
    }

    #updatePageRoot = (path) => {
      const $button = $(".aem-modernize-job-add-pages");
      $button.removeData("foundation-picker-control.internal.state"); // Clear state - need to redraw picker.
      let src = $button[0].dataset.foundationPickerControlSrc;
      const regex = /root=(.+?)&/;
      const matches = src.match(regex);
      src =  src.replace(matches[1], encodeURIComponent(path));
      $button[0].dataset.foundationPickerControlSrc = src;
    }

    #setup = () => {
      const _this = this;
      const registry = $(window).adaptTo("foundation-registry");

      $(document).on("change", ".aem-modernize-job-source-root", function(e) {
        _this.#updatePageRoot(e.target.value);
      });

      $(document).on("change", ".aem-modernize-page-handling", function(e) {
        const sourceField = $("input[name='sourceRoot']").closest("foundation-autocomplete").adaptTo("foundation-field");
        const targetField = $("input[name='targetRoot']").closest("foundation-autocomplete").adaptTo("foundation-field");
        const needs = (e.target.checked && e.target.value === "COPY")
        if (sourceField && targetField) {
          sourceField.setDisabled(!needs);
          sourceField.setRequired(needs);

          targetField.setDisabled(!needs);
          targetField.setRequired(needs);
          _this.#updateNext();
        }
        if (!needs) {
          _this.#updatePageRoot("/content");
        }
      });

      registry.register("foundation.picker.control.action", {
        name: "aem.modernize.addcontent",
        handler: (name, el, config, selections) => {
          let showNotice = false;
          if (selections.length > 0) {
            _this.#ui.wait();
            const promises = [];

            selections.forEach(function(selection) {
              const path = selection.value;
              if (path !== "/content") {
                const item = _this.#getItem(path);
                if (!item) {
                  promises.push(
                    _this.#populateItem({path: path})
                      .then((item) => {
                        return new Promise((resolve) => {
                          _this.#addTableRow(item);
                          _this.#addHidden(item);
                          resolve(item);
                        });
                      })
                  );
                } else {
                  showNotice = true;
                }
              }
            });

            Promise.all(promises)
              .then(_this.#refreshPageList)
              .catch(_this.#showError)
              .finally(() => {
                _this.#ui.clearWait();
                if (showNotice) {
                  _this.#ui.notify(Granite.I18n.get("Error"), Granite.I18n.get("Selected page already exist in the list."), "notice");
                }
              });
          }
        }
      });

      this.#$wizard.find(".aem-modernize-collection-action-delete").on("click", (e) => {
        e.preventDefault();
        e.stopPropagation();

        this.#ui.prompt(
          Granite.I18n.get("Remove"),
          Granite.I18n.get("Are you sure you want to remove the selected pages?"),
          "warn",
          [
            {id: "no", text: Granite.I18n.get("Cancel")},
            {id: "yes", text: Granite.I18n.get("Remove"), warning: true}
          ],
          (id) => {
            if (id === "yes") {
              this.#remove();
            }
          }
        );
      });

      $(document).on("click", ".aem-modernize-job-includechildren-dialog-confirm", (e) => {
        e.preventDefault()
        e.stopPropagation();
        $(e.target).closest(".aem-modernize-job-includechildren-dialog").find("button[coral-close]").click();
        _this.#ui.wait();
        const url = Granite.HTTP.externalize(Granite.HTTP.getPath(window.location.href) + ".listchildren.json")
        const params = $("#aem-modernize-job-includechildren-dialog-form").serialize();
        $.getJSON(url, params, (data) => {
          if (data.total === 0) {
            _this.#ui.clearWait();
            _this.#ui.notify(Granite.I18n.get("Info"), Granite.I18n.get("There were no children for the selected page."));
          } else {
            _this.#addChildren(data.paths);
          }
        }).fail(() => {
          _this.#ui.clearWait();
          _this.#ui.notify(Granite.I18n.get("Error"), Granite.I18n.get("Unable to retrieve children for the selected page."), "error")
        });
      });


      this.#$form.on("submit", (e) => {
        e.stopPropagation();
        e.preventDefault();
        _this.#ui.wait();

        _this.#checkConfPermissions()
          .then(_this.#checkTargetPermissions)
          .then(() => {
            const formData = _this.#getFormData();
            _this.#ui.clearWait();
            this.#ui.prompt(
              Granite.I18n.get("Convert Pages"),
              Granite.I18n.get("You are about to submit {0} items(s) for conversion. Are you sure?", formData.paths.length, "The current selection count"),
              "warn",
              [
                {id: "no", text: Granite.I18n.get("Cancel")},
                {id: "yes", text: Granite.I18n.get("Convert"), warning: true}
              ],
              (id) => {
                if (id === "yes") {
                  this.#scheduleJob(formData);
                }
              }
            );
          }).catch(() => {
          _this.#ui.clearWait();
          _this.#ui.prompt(
            Granite.I18n.get("Error"),
            Granite.I18n.get("You do not have permission to one or more items in this job description."),
            "error",
            [{id: "no-permissions", text: Granite.I18n.get("Ok")}]
          );
        });
      });

      Coral.commons.ready(this.#$wizard[0], () => {
        _this.#refreshPageList();
        _this.#paginate();
      });
    }
  }

  AemModernize.CreateJobForm = CreateJobForm;

})(document, Granite, Granite.$, AemModernize);
