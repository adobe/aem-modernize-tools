/*
 *  (c) 2019 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
$(document).ready(function () {
    'use strict';

    var REMOTE_POLICIES_IMPORT_SERVICE_PATH = "/libs/cq/modernize/design";

    var ui = $(window).adaptTo("foundation-ui");

    var designPropertiesRows;
    var convertedRows = [];
    var policiesImporterContainer = document.querySelector(".js-aem-PoliciesImporter-container");
    var designPathField = document.querySelector(".js-aem-PoliciesImporter-designPath");
    var infoText = document.querySelector(".js-aem-PoliciesImporter-infoText");
    var showPropertiesButton = document.querySelector(".js-aem-PoliciesImporter-showProperties");
    var backButton = document.querySelector(".js-aem-PoliciesImporter-back");

    var targetPathField = document.querySelector(".cq-cloudconfig-configpathbrowser foundation-autocomplete"); // TODO: More robust
    var importDesignPropertiesButton = document.querySelector(".js-aem-PoliciesImporter-importDesignProperties");
    var DEFAULT_IMPORT_POLICIES_BUTTON = importDesignPropertiesButton.textContent;

    var designSearch = document.querySelector(".js-aem-PoliciesImporter-designSearch");
    var importedPolicies = document.querySelector(".js-aem-PoliciesImporter-importedPolicies");
    var designPropertiesTable = document.querySelector(".js-aem-PoliciesImporter-designProperties");

    function updateRepositoryPathParameter() {
        if (!designPathField) {
            return;
        }
        window.location = window.location.pathname + "?designPath=" + designPathField.value;
    }

    function adjustImportSection(selectionCount) {
        if (selectionCount > 0) {
            targetPathField.disabled = importDesignPropertiesButton.disabled = false;
            importDesignPropertiesButton.textContent = Granite.I18n.get("Import {0} design properties(s)", selectionCount, "Number of design properties to be imported");
        } else {
            targetPathField.disabled = importDesignPropertiesButton.disabled = true;
            importDesignPropertiesButton.textContent = DEFAULT_IMPORT_POLICIES_BUTTON;
        }
    }

    function init() {
        designPropertiesRows = designPropertiesTable.items.getAll();

        for (var i = 0, length = designPropertiesRows.length; i < length; i++) {
            var row = designPropertiesRows[i];

            if (row.hasAttribute("data-modernize-design-imported")) {
                convertedRows.push(row);
            }
        }

        if (infoText) {
            infoText.textContent = "";

            if (designPropertiesRows && designPropertiesRows.length > 0) {
                if (designPropertiesRows.length === 1 && !designPropertiesRows[0].dataset["foundationCollectionItemId"]) {
                    // The empty row, hide the infoText
                    infoText.setAttribute("hidden", true);
                } else {
                    infoText.textContent = Granite.I18n.get("Found {0} component style(s)", designPropertiesRows.length);
                }
            }
        }

        showPropertiesButton.on("click", updateRepositoryPathParameter);
        backButton.on("click", updateRepositoryPathParameter);

        designPropertiesTable.on("coral-table:change", function (event) {
            var selection = event && event.detail && event.detail.selection ? event.detail.selection : [];
            var count = selection.length;

            // Deselect already imported policies
            for (var i = 0; i < selection.length; i++) {
                var row = selection[i];
                if (row.hasAttribute("data-modernize-policies-imported")) {
                    // Don't trigger too many events
                    row.set('selected', false, true);
                    count--;
                }
            }

            adjustImportSection(count);
        });

        importDesignPropertiesButton.on("click", function () {
            // Get paths from table
            var paths = [];

            var selectedRows = designPropertiesTable.selectedItems;
            for (var i = 0, length = selectedRows.length; i < length; i++) {
                var value = selectedRows[i].dataset["foundationCollectionItemId"];

                if (value) {
                    paths.push(value);
                }
            }

            var url = REMOTE_POLICIES_IMPORT_SERVICE_PATH + "/content/import.json";
            var data = {
                paths: paths,
                designPath: designPathField.value,
                targetPath: targetPathField.value
            };

            // show overlay and wait
            ui.wait();

            $.post(url, data, function (data) {
                importDesignPropertiesButton.disabled = true;
                backButton.hidden = false;
                designSearch.hidden = true;
                importedPolicies.hidden = false;

                // build result table
                var count = 0;
                var successCount = 0;
                var errorCount = 0;

                if (Object.keys(data).length) {
                    importedPolicies.items.clear();
                }

                for (var path in data) {
                    count++;

                    // Create a row for the results table
                    var row = new Coral.Table.Row();
                    importedPolicies.items.add(row);

                    // Create a cell that will contain the path to the design style
                    var pathCell = new Coral.Table.Cell();
                    pathCell.textContent = path;

                    row.appendChild(pathCell);

                    var links = "-";
                    var message = Granite.I18n.get("Successfully imported policy");
                    var resultPath = data[path].resultPath;

                    if (resultPath) {
                        // success
                        successCount++;

                        var crxHref = Granite.HTTP.externalize("/crx/de/index.jsp#" + resultPath.replace(":", "%3A"));
                        links = '<a href="' + crxHref + '" target="_blank" class="coral-Link">crxde</a>';
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
                var successes = Granite.I18n.get("Imported <strong>{0}</strong> design properties(s)", successCount, "");
                var errors = Granite.I18n.get("Failed importing <strong>{0}</strong> design properties(s)", errorCount, "");
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
                var message = Granite.I18n.get("Call to policies import servlet failed. Please view the logs.");
                ui.alert(title, message, "error");
            }).always(function () {
                ui.clearWait();
            });
        });

    }

    Coral.commons.ready(policiesImporterContainer, init);
});
