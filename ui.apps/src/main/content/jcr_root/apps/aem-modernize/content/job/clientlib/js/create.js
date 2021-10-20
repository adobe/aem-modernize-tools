(function(document, Granite, $, AemModernize) {
  "use strict";

  class CreateJobForm {

    static #NO_CONTENT = Granite.I18n.get("There are no items.");
    static #EMPTY_ROW = '<tr is="coral-table-row" class="empty-row"><td is="coral-table-cell" alignment="center">' + CreateJobForm.#NO_CONTENT + '</td></tr>';

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

    $getRow(item) {
      return $('<tr is="coral-table-row" class="empty-row"><td is="coral-table-cell" alignment="center">Type Specific Must Override Row Creation</td></tr>');
    }

    $getForm() {
      return this.#$form;
    }

    $getWizard() {
      return this.#$wizard;
    }

    checkPermissions(item) {
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

    getPageData(item) {
      return new Promise((resolve, reject) => {
        const url = Granite.HTTP.externalize(item.path + "/jcr:content.json", true)
        $.getJSON(url, (data) => {
          item.title = data["jcr:title"]
          resolve(item);
        }).fail(() => {
          reject(item);
        });
      });
    }

    getRules(item){
      return new Promise((resolve) => {
        resolve(item);
      });
    }

    addHidden(item) {}

    removeHidden($row) {}

    getFormData($form) {
      return {
        name: $form.find("input[name='name']")[0].value,
        type: this.operation
      }
    }

    // Private Methods

    #getItem = (path) => {
      let retVal = undefined;
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
        let more = paginator.hasNext;
        if (more === undefined) {
          more = selected.length >= (paginator.offset + paginator.limit);
        }
        if (more && this.#$table[0].items.getAll().length === 0) {
          paginator.restart(paginator.offset, more);
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
            promises.push(this.populateItem({path: path}).then((item) => {
              return new Promise((resolve) => {
                if (this.#$table[0].items.getAll().length <= (offset + paginator.limit)) {
                  this.#addTableRow(item);
                  paginator.offset = this.#$table[0].items.getAll().length;
                } else {
                  this.addHidden(item);
                }
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

    #refreshPageList = () => {
      this.#$table[0].trigger("foundation-selections-change");
      const next = this.#$wizard.find(".aem-modernize-job-create-next")[0];
      if (this.#$wizard.pageList.length === 0) {
        this.#$table[0].items.add($(CreateJobForm.#EMPTY_ROW)[0]);
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
      if (item.path) {
        if (item.hasPermission === false) {
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
        limit: 30,
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

    #scheduleJob = (data) => {
      const _this = this;
      this.#ui.wait();
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
          location.href = Granite.HTTP.externalize(this.#$form.data("aemModernizeJobViewUrl")+ json.job);
        },
        error: (xhr, status, error) => {
          _this.#ui.clearWait();
          const json = xhr.responseJSON;
          let message = json && json.message || error;
          _this.#ui.notify(Granite.I18n.get("Error"), Granite.I18n.get(message), "error");
        }
      });
    }

    #setup = () => {
      const _this = this;
      const registry = $(window).adaptTo("foundation-registry");
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
                    _this.populateItem({path: path})
                      .then((item) => {
                        return new Promise((resolve) => {
                          _this.#addTableRow(item);
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
        const url = $(".aem-modernize-job-form").data("aemModernizeListChildrenUrl");
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


        const formData = _this.getFormData(this.#$form);
        this.#ui.prompt(
          Granite.I18n.get("Convert Pages"),
          Granite.I18n.get("You are about to submit {0} page(s) for conversion. Are you sure?", formData.paths.length, "The current selection count"),
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

      });

      Coral.commons.ready(this.#$wizard[0], () => {
        _this.#refreshPageList();
        _this.#paginate()
      });
    }
  }

  AemModernize.CreateJobForm = CreateJobForm;

})(document, Granite, Granite.$, AemModernize);
