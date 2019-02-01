/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
$(document).ready(function () {
    'use strict';

    var REMOTE_DIALOG_CONVERSION_SERVICE_PATH = "/libs/cq/modernize/dialog";;

    var ui = $(window).adaptTo("foundation-ui");

    var dialogRows;
    var convertedRows = [];
    var dialogConverterContainer = document.querySelector(".js-cq-DialogConverter-container");
    var showConverted = document.querySelector(".js-cq-DialogConverter-showConvertedDialogRows");
    var searchPathField = document.querySelector(".js-cq-DialogConverter-searchPath");
    var infoText = document.querySelector(".js-cq-DialogConverter-infoText");
    var showDialogsButton = document.querySelector(".js-cq-DialogConverter-showDialogs");
    var backButton = document.querySelector(".js-cq-DialogConverter-back");
    var convertDialogsButton = document.querySelector(".js-cq-DialogConverter-convertDialogs");
    var dialogSearch = document.querySelector(".js-cq-DialogConverter-dialogSearch");
    var convertedDialogs = document.querySelector(".js-cq-DialogConverter-convertedDialogs");
    var dialogTable = document.querySelector(".js-cq-DialogConverter-dialogs");

    function updateRepositoryPathParameter () {
        if (!searchPathField) {
            return;
        }

        var path = searchPathField.value;
        // Set the path query parameter
        window.location = window.location.pathname + "?path=" + path;
    }

    function adjustConvertButton (selectionCount) {
        // adjust button label
        convertDialogsButton.textContent = Granite.I18n.get("Convert {0} dialog(s)", selectionCount, "Number of dialogs to be converted");
    }

    function init () {
        dialogRows = dialogTable.items.getAll();

        for (var i = 0, length = dialogRows.length; i < length; i++) {
            var row = dialogRows[i];

            if (row.hasAttribute("data-dialogconversion-dialog-converted")) {
                convertedRows.push(row);
            }
        }

        if (infoText) {
          infoText.textContent = "";

          if (dialogRows && dialogRows.length > 0) {
            if (dialogRows.length === 1 && !dialogRows[0].dataset["foundationCollectionItemId"]) {
              // The empty row, hide the infoText
              infoText.setAttribute("hidden", true);
            } else {
              infoText.textContent = Granite.I18n.get("Found {0} dialog(s)", dialogRows.length);
            }
          }
        }

        showDialogsButton.on("click", updateRepositoryPathParameter);
        backButton.on("click", updateRepositoryPathParameter);

        dialogTable.on("coral-table:change", function (event) {
            var selection = event && event.detail && event.detail.selection ? event.detail.selection : [];
            var count = selection.length;

            // Deselect already converted dialogs
            for (var i = 0; i < selection.length; i++) {
                var row = selection[i];
                if (row.hasAttribute("data-dialogconversion-dialog-converted")) {
                    // Don't trigger too many events
                    row.set('selected', false, true);
                    count--;
                }
            }

            adjustConvertButton(count);
        });

        showConverted.on("change", function () {
            for (var i = 0, length = convertedRows.length; i < length; i++) {
                var row = convertedRows[i];

                if (showConverted.checked) {
                    row.removeAttribute("hidden");
                } else {
                    row.setAttribute("hidden", true);
                }
            }
        });

        convertDialogsButton.on("click", function () {
            // get paths from table
            var paths = [];

            var selectedDialogRows = dialogTable.selectedItems;
            for (var i = 0, length = selectedDialogRows.length; i < length; i++) {
                var value = selectedDialogRows[i].dataset["foundationCollectionItemId"];

                if (value) {
                    paths.push(value);
                }
            }

            var url = REMOTE_DIALOG_CONVERSION_SERVICE_PATH + "/content/convert.json";
            var data = {
                paths : paths
            };

            // show overlay and wait
            ui.wait();

            $.post(url, data, function (data) {
                convertDialogsButton.hidden = true;
                backButton.hidden = false;
                dialogSearch.hidden = true;
                convertedDialogs.hidden = false;

                // build result table
                var count = 0;
                var successCount = 0;
                var errorCount = 0;

                if (Object.keys(data).length) {
                    convertedDialogs.items.clear();
                }

                for (var path in data) {
                    count++;

                    // Create a row for the results table
                    var row = new Coral.Table.Row();
                    convertedDialogs.items.add(row);

                    // Create a cell that will contain the path to the dialog
                    var pathCell = new Coral.Table.Cell();
                    pathCell.textContent = path;

                    row.appendChild(pathCell);

                    var links = "-";
                    var message = Granite.I18n.get("Successfully converted dialog");
                    var resultPath = data[path].resultPath;

                    if (resultPath) {
                        // success
                        successCount++;

                        var href = Granite.HTTP.externalize(resultPath) + ".html";
                        var crxHref = Granite.HTTP.externalize("/crx/de/index.jsp#" + resultPath.replace(":", "%3A"));
                        links = '<a href="' + href + '" target="_blank" class="coral-Link">show</a> / <a href="' + crxHref + '" target="_blank" class="coral-Link">crxde</a>';
                    } else {
                        // error
                        errorCount++;
                        message = Granite.I18n.get("Error");

                        if (data[path].errorMessage) {
                            message += ": " + data[path].errorMessage;
                        }
                    }

                    // Create the cell that contains the links
                    var linksCell = new Coral.Table.Cell();
                    row.appendChild(linksCell);
                    linksCell.innerHTML = links;

                    // Add the cell that contains the message
                    var messageCell = new Coral.Table.Cell();
                    messageCell.textContent = message;
                    row.appendChild(messageCell);
                }

                var type = "success";
                var successes = Granite.I18n.get("Converted <strong>{0}</strong> dialog(s)", successCount, "");
                var errors = Granite.I18n.get("Failed converting <strong>{0}</strong> dialog(s)", errorCount, "");
                var content = successes;

                if (errorCount > 0 && successCount > 0) {
                    // mixed (errors and successes)
                    type = "warning";
                    content = errors + " : " + successes;
                } else if (errorCount > 0) {
                    // error
                    type = "error";
                    content = errors;
                }

                ui.notify(undefined, content, type);

            }).fail(function () {
                var title = Granite.I18n.get("Error");
                var message = Granite.I18n.get("Call to dialog conversion servlet failed. Please view the logs.");
                ui.alert(title, message, "error");
            }).always(function () {
                ui.clearWait();
            });
        });
    }

    Coral.commons.ready(dialogConverterContainer, init);
});
