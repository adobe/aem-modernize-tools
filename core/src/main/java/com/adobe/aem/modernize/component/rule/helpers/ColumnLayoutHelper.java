package com.adobe.aem.modernize.component.rule.helpers;

/*-
 * #%L
 * AEM Modernize Tools - Core
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.jackrabbit.JcrConstants.*;
import static org.apache.sling.jcr.resource.api.JcrResourceConstants.*;

/**
 * Helper class to manage column layout operations for the ColumnControlRewriteRule.
 * Extracts complex column operations into a separate utility class.
 */
public class ColumnLayoutHelper {

    private static final Logger logger = LoggerFactory.getLogger(ColumnLayoutHelper.class);
    private static final Pattern COLUMN_PATTERN = Pattern.compile("^(\\w+)=\\[([0-9,]+)\\]$");

    protected static final String PN_WIDTH = "width";
    protected static final String PN_OFFSET = "offset";
    protected static final String PN_BEHAVIOR = "behavior";
    protected static final String PROP_NEWLINE = "newline";
    protected static final String NN_RESPONSIVE_CONFIG = "cq:responsive";

    private final Map<String, long[]> widths = new HashMap<>();
    private final String columnControlResourceType;
    private final int columns;

    /**
     * Creates a new ColumnLayoutHelper.
     *
     * @param columnControlResourceType The resource type of the column control component
     * @param columns The number of columns in the layout
     */
    public ColumnLayoutHelper(String columnControlResourceType, int columns) {
        this.columnControlResourceType = columnControlResourceType;
        this.columns = columns;
    }

    /**
     * Adds a column width configuration.
     *
     * @param configName The name of the configuration (e.g., "default")
     * @param columnWidths Array of widths for each column
     * @return this helper instance for chaining
     */
    public ColumnLayoutHelper addWidthConfiguration(String configName, long[] columnWidths) {
        if (columnWidths.length != columns) {
            throw new IllegalArgumentException(
                    "Column width array length (" + columnWidths.length +
                            ") doesn't match column count (" + columns + ")");
        }
        widths.put(configName, columnWidths);
        return this;
    }

    /**
     * Parse column width configurations from an array of strings.
     *
     * @param widthDefinitions Array of strings in the format "name=[width1,width2,...]"
     * @return this helper instance for chaining
     * @throws IllegalArgumentException if any definition is invalid
     */
    public ColumnLayoutHelper parseWidthConfigurations(String[] widthDefinitions) {
        for (String def : widthDefinitions) {
            Matcher matcher = COLUMN_PATTERN.matcher(def);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid format for width configuration: " + def);
            }

            String name = matcher.group(1);
            String[] widthStrs = matcher.group(2).split(",");

            if (widthStrs.length != columns) {
                throw new IllegalArgumentException(
                        "Number of columns (" + widthStrs.length +
                                ") doesn't match layout format (" + columns + ")");
            }

            long[] widths = new long[columns];
            try {
                for (int i = 0; i < columns; i++) {
                    widths[i] = Long.parseLong(widthStrs[i]);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Column width definitions must be numeric values", e);
            }

            this.widths.put(name, widths);
        }

        return this;
    }

    /**
     * Checks if a node is a column node based on its resource type.
     *
     * @param node The node to check
     * @return true if the node is a column node
     */
    public boolean isColumnNode(Node node) throws RepositoryException {
        if (!node.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)) {
            return false;
        }
        return StringUtils.equals(
                columnControlResourceType,
                node.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString());
    }

    /**
     * Finds the first column node among the node's children.
     *
     * @param siblings Iterator of sibling nodes
     * @param layoutValue The value of the layout property to match
     * @return the first column node or null if none found
     */
    @Nullable
    public Node findFirstColumn(NodeIterator siblings, String layoutValue) throws RepositoryException {
        Node found = null;

        while (siblings.hasNext() && found == null) {
            Node node = siblings.nextNode();

            // Check if node has the correct resource type
            if (!node.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)) {
                continue;
            }

            Property resourceTypeProperty = node.getProperty(SLING_RESOURCE_TYPE_PROPERTY);
            if (!StringUtils.equals(columnControlResourceType, resourceTypeProperty.getString())) {
                continue;
            }

            // Check if node has the correct layout value
            final String PN_LAYOUT = "layout";
            if (!node.hasProperty(PN_LAYOUT)) {
                continue;
            }

            Property layoutProperty = node.getProperty(PN_LAYOUT);
            if (!StringUtils.equals(layoutValue, layoutProperty.getString())) {
                continue;
            }

            found = node;
        }

        return found;
    }

    /**
     * Gets the content of each column as a list of queues containing node names.
     *
     * @param root The root node
     * @return A list of queues, each representing a column's content
     */
    @NotNull
    public List<Queue<String>> getColumnContent(Node root) throws RepositoryException {
        List<Queue<String>> columnContents = new ArrayList<>(columns);
        NodeIterator siblings = root.getNodes();

        // Skip until we find the first column node
        while (siblings.hasNext()) {
            Node current = siblings.nextNode();
            if (isColumnNode(current)) {
                break;
            }
        }

        // Process each column
        for (int i = 0; i < columns; i++) {
            Queue<String> nodeNames = new LinkedList<>();
            columnContents.add(nodeNames);

            // Process nodes until we hit the next column break or run out of nodes
            while (siblings.hasNext()) {
                Node node = siblings.nextNode();
                if (isColumnNode(node)) {
                    // Found the next column break
                    break;
                }
                nodeNames.add(node.getName());
            }
        }

        return columnContents;
    }

    /**
     * Adds responsive grid configuration to a node.
     *
     * @param node The node to configure
     * @param columnIndex The index of the column
     * @param isNewline Whether this column should start a new line
     * @param isOffset Whether this column should have an offset
     */
    //Following move method refactoring technique, Moved the addResponsive method from ColumnControlRewriteRule to the new helper class
    public void addResponsiveConfiguration(
            Node node,
            int columnIndex,
            boolean isNewline,
            boolean isOffset) throws RepositoryException {

        Node responsive = node.addNode(NN_RESPONSIVE_CONFIG, NT_UNSTRUCTURED);

        for (String breakpoint : widths.keySet()) {
            Node entry = responsive.addNode(breakpoint, NT_UNSTRUCTURED);

            // Set width for this breakpoint
            long width = widths.get(breakpoint)[columnIndex];
            entry.setProperty(PN_WIDTH, Long.toString(width));

            // Calculate and set offset if needed
            long offset = 0;
            if (isOffset && columnIndex > 0) {
                for (int i = 0; i < columnIndex; i++) {
                    offset += widths.get(breakpoint)[i];
                }
            }
            entry.setProperty(PN_OFFSET, Long.toString(offset));

            // Set newline behavior if needed
            if (isNewline) {
                entry.setProperty(PN_BEHAVIOR, PROP_NEWLINE);
            }
        }
    }

    /**
     * Gets the number of columns in this layout.
     *
     * @return the number of columns
     */
    public int getColumnCount() {
        return columns;
    }

    /**
     * Gets a formatted string representation of all width configurations.
     *
     * @return a formatted string of width configurations
     */
    public String getFormattedWidthConfigurations() {
        List<String> formattedConfigs = new ArrayList<>();

        for (Map.Entry<String, long[]> entry : widths.entrySet()) {
            String key = entry.getKey();
            long[] values = entry.getValue();

            StringBuilder sb = new StringBuilder();
            sb.append(key).append("=[");

            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(values[i]);
            }

            sb.append("]");
            formattedConfigs.add(sb.toString());
        }

        return StringUtils.join(formattedConfigs, ", ");
    }
}