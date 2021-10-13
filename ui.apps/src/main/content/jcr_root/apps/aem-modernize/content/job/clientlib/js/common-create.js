(function(document, Granite, Coral, AemModernize) {
  "use strict";

  class CreateJobForm {

    static #NO_CONTENT = Granite.I18n.get("There is no item.");
    static #EMPTY_ROW = '<tr is="coral-table-row" class="empty-row"><td is="coral-table-cell" colspan="4" alignment="center">' + CreateJobForm.#NO_CONTENT + '</td></tr>';

    #ui;
    #messenger;
    #$form;
    #$wizard;
    #$table;
    #operation


    constructor() {
      this.#ui = $(window).adaptTo("foundation-ui");
      this.#messenger = $(window).adaptTo("foundation-util-messenger");
      this.#$form = $(".aem-modernize-job-form");
      this.#$wizard = this.#$form.find(".aem-modernize-job-wizard");
      this.#$table = this.#$wizard.find(".aem-modernize-job-pages");
      this.#$wizard.pageList = [];
      this.#operation = this.#$form.data('aem-modernize-operation');
      this.#setup()
    }

    $getWizard() {
      return this.#$wizard;
    }

    $getRow(item) {
      return $('<tr is="coral-table-row" class="empty-row"><td is="coral-table-cell" colspan="4" alignment="center">Type Specific Must Override Row Creation</td></tr>');
    }

    addHidden(item) {
      // Page Path
      if (this.#$wizard.find("input[type='hidden'][name='path'][value='" + item.path + "']").length === 0) {
        const $hidden = $('<input type="hidden">').attr("name", "path").attr("data-path", item.path).attr("value", item.path);
        this.#$wizard.append($hidden);
      }
    }

    removeHidden($row) {
      const id = $row.data("foundationCollectionItemId");
      // Remove row from Wizard and hidden input
      for (let itemIdx = 0; itemIdx < this.#$wizard.pageList.length; itemIdx++ ) {
        if (this.#$wizard.pageList[itemIdx].path === id) {
          this.#$wizard.pageList.splice(itemIdx, 1);
          $("input[type='hidden'][name='path'][value='" + id + "']").remove();
          break;
        }
      }
    }
    // Private Methods

    #getItem = (path) => {
      let retVal = null;
      if (this.#$wizard.pageList && this.#$wizard.pageList.length > 0) {
        for (let index = 0; index < this.#$wizard.pageList.length; index++) {
          if (this.#$wizard.pageList[index].path === path) {
            retVal = this.#$wizard.pageList[index];
            break;
          }
        }
      }
      return retVal;
    }

    #append = (path) => {
      const item = {};
      item.path = path;
      return this.#checkPermissions(item)
        .then(this.#getPageData)
        .then(this.#getRules)
        .then((item) => {
          return new Promise((resolve) => {
            this.#addTableRow(item);
            resolve(item);
          });
        });
    }

    #checkPermissions = (item) => {
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

    #getPageData = (item) => {
      return new Promise((resolve, reject) => {
        const url = Granite.HTTP.externalize(item.path + "/jcr:content.json", true)
        $.getJSON(url, (data) => {
          item.title = data['jcr:title']
          resolve(item);
        }).fail(() => {
          reject(item);
        });
      });
    }

    #getRules = (item) => {
      return new Promise((resolve, reject) => {
        const params = {
          path: item.path,
          operation: this.#operation
        }

        const url = this.#$form.data("aemModernizeRuleUrl");
        $.getJSON(url, params, (data) => {
          item.templateRules = data.rules.templateRules;
          item.policyRules = data.rules.policyRules;
          item.componentRules = data.rules.componentRules;
          resolve(item);
        }).fail(() => {
          reject(item);
        });
      });
    }

    #addTableRow = (item) => {
      const paginator = this.#$table.data("foundation-layout-table.internal.paginator");
      this.#$wizard.pageList.splice(paginator.offset, 0, item);
      this.#$table[0].items.add(this.$getRow(item)[0])
      this.#$table.trigger("coral-collection:add");
      this.addHidden(item)
    }


    #remove = () => {
      const selected = this.#$table[0].selectedItems;
      if (selected != null && selected.length > 0) {
        for (let selectedIdx = 0; selectedIdx < selected.length; selectedIdx++) {
          const $current = $(selected[selectedIdx]);
          this.#$table[0].items.remove($current[0])
          this.removeHidden($current);
        }
        const paginator = this.#$table.data("foundation-layout-table.internal.paginator");
        paginator.restart();
        this.#refreshPageList();
      }
    }

    #refreshPageList = () => {
      this.#$wizard.trigger("foundation-selections-change");
      const next = this.#$wizard.find(".aem-modernize-job-create-next")[0];
      if (this.#$wizard.pageList.length === 0) {
        this.#$table[0].items.add($(CreateJobForm.#EMPTY_ROW)[0]);
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
      if (item.path) {
        if (!item.hasPermission) {
          this.#ui.prompt(
            Granite.I18n.get("Error"),
            Granite.I18n.get("You do not have permission to convert that content."),
            "error",
            [{id: "no-permissions", text: Granite.I18n.get("Ok")}]
          );
        }
      } else {
        throw item;
      }
    }

    #paginate = () => {
      const _this = this;
      var scrollContainer = this.#$table.children("[coral-table-scroll]");

      const Paginator = $(window).adaptTo("foundation-util-paginator");
      const paginator = new Paginator({
        el: scrollContainer,
        limit: 10,
        hasNext: false,
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
            const items = _this.#$wizard.pageList.slice(this.offset, this.offset + this.limit);
            items.forEach((item) => {
              _this.#addTableRow(item)
            });
            const more = this.#$wizard.pageList.length > (this.offset + this.limit);
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

    #setup = () => {
      const _this = this;
      const registry = $(window).adaptTo("foundation-registry");
      registry.register("foundation.picker.control.action", {
        name: "aem.modernize.addcontent",
        handler: (name, el, config, selections) => {
          if (selections.length > 0) {
            _this.#ui.wait();
            const promises = [];

            selections.forEach(function(selection) {
              const path = selection.value;
              if (path !== '/content') {
                const item = _this.#getItem(path);
                if (item == null) {
                  const promise = _this.#append(path);
                  promises.push(promise);
                } else {
                  _this.#ui.notify(Granite.I18n.get("Error"), Granite.I18n.get("Selected page already exist in the list."), "notice")
                }
              }
            });

            Promise.all(promises)
              .then(_this.#refreshPageList)
              .catch(_this.#showError)
              .finally(() => {
                _this.#ui.clearWait();
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
      Coral.commons.ready(_this.#$wizard[0], () => {
        _this.#refreshPageList();
        _this.#paginate()
      });
    }
  }

  AemModernize.CreateJobForm = CreateJobForm;

})(document, Granite, Coral, AemModernize);
