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

package com.adobe.aem.modernize.structure.impl.rules;

import java.util.Dictionary;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites a Column Control component into multiple Responsive Grid layout components. Each column in the
 * original control will become a grid instance with all of the appropriately nested components.
 *
 * The layout is mapped to column spans within the responsive grid.
 */
@Service
@Component(
        configurationFactory = true,
        policy = ConfigurationPolicy.REQUIRE,
        metatype = true,
        label="Column Control Rewrite Rule", description="Rewrites Column control components to responsive grid replacements.")
@Properties({
        @Property(name="service.ranking", intValue = 3)
})
public class ColumnControlRewriteRule implements StructureRewriteRule {

    private static final Logger logger = LoggerFactory.getLogger(ColumnControlRewriteRule.class);

    private int ranking = Integer.MAX_VALUE;
    private static final String PROP_LAYOUT = "layout";
    private static final String PROP_CONTROL_TYPE = "controlType";

    private static final String CONTROL_TYPE_BREAK = "break";
    private static final String CONTROL_TYPE_END = "end";

    private static final String RESPONSIVE_GRID_NAME = "responsivegrid";
    private static final String DEFAULT_LAYOUT_NAME = "default";

    private static final String PROP_WIDTH = "width";
    private static final String PROP_OFFSET = "offset";

    private static final String PROP_RESOURCE_TYPE_DEFAULT = "foundation/components/parsys/colctrl";

    @Property(label = "Column Control ResourceType",
            description = "The sling:resourceType of this column control, leave blank to use Foundation component. (some customers extend/create their own)",
            value = PROP_RESOURCE_TYPE_DEFAULT)
    private static final String PROP_RESOURCE_TYPE= "sling.resourceType";

    private String resourceType = PROP_RESOURCE_TYPE_DEFAULT;

    @Property(label = "Layout Property Value",
        description = "The value of the `layout` property on the primary column control component.")
    private static final String PROP_LAYOUT_VALUE = "layout.value";

    private String layout;

    @Property(label = "Column Widths",
            description = "An array of widths for each of the columns in target grid. (e.g. 50%/50% column control is [6,6] in target grid. 75%/25% => [8,4])",
            cardinality = Integer.MAX_VALUE)
    private static final String PROP_COLUMN_WIDTHS = "column.widths";

    private long[] widths;

    @Override
    public boolean matches(Node root) throws RepositoryException {
        if (!root.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
            return false;
        }
        javax.jcr.Property prop = root.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
        if (!StringUtils.equals(resourceType, prop.getString())) {
            return false;
        }

        if (!root.hasProperty(PROP_LAYOUT)) {
            return false;
        }
        prop = root.getProperty(PROP_LAYOUT);
        if (!StringUtils.equals(layout, prop.getString())) {
            return false;
        }

        // Check Column breaks
        Node parent = root.getParent();
        NodeIterator siblings = parent.getNodes();
        while (siblings.hasNext()) {
            Node sibling = siblings.nextNode();
            if (sibling.getName().equals(root.getName())) {
                break;
            }
        }

        // From here, find the column breaks.
        int count = widths.length;
        while (siblings.hasNext()) {
            Node sibling = siblings.nextNode();
            if (sibling.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY) &&
                StringUtils.equals(
                        sibling.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(),
                        resourceType)) {

                // If it has a layout, then it's a new column control,
                // This shouldn't ever happen, but who knows.
                if (sibling.hasProperty(PROP_LAYOUT) && count != 0) {
                    return false;

                // If we hit a break or end control, reduce the number of columns
                } else if (sibling.hasProperty(PROP_CONTROL_TYPE) &&
                        (StringUtils.equals(sibling.getProperty(PROP_CONTROL_TYPE).getString(), CONTROL_TYPE_BREAK)
                        || StringUtils.equals(sibling.getProperty(PROP_CONTROL_TYPE).getString(), CONTROL_TYPE_END))) {
                    count--;

                    // If we hit the end, then break out of the loop, as we're done counting columns
                    if (StringUtils.equals(sibling.getProperty(PROP_CONTROL_TYPE).getString(), CONTROL_TYPE_END)) {
                        break;
                    }
                }
            }
        }
        return count == 0;
    }

    /**
     * Updates this node, the returned node is the primary column control resource, although this updates a number of sibling resources.
     * @param root The root of the subtree to be rewritten
     * @param finalNodes
     * @return
     * @throws RewriteException
     * @throws RepositoryException
     */
    @Override
    public Node applyTo(Node root, Set<Node> finalNodes) throws RewriteException, RepositoryException {

        Node node = null;
        Node parent = root.getParent();
        NodeIterator siblings = parent.getNodes();

        // Find the root in the sibling list
        while (siblings.hasNext()) {
            node = siblings.nextNode();
            if (matches(node)) {
                break;
            }
        }

        String firstNodeName = null;
        for (int i = 0; i < widths.length; i++) {

            // Create the new first responsive grid.
            String gridName = JcrUtil.createValidChildName(parent, RESPONSIVE_GRID_NAME);
            Node grid = parent.addNode(gridName, JcrConstants.NT_UNSTRUCTURED);
            if (i == 0){
                firstNodeName = grid.getName();
            }
            parent.orderBefore(grid.getName(), node.getName());
            grid.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, RewriteUtils.RESPONSIVE_GRID_RES_TYPE);
            Node gridConfig = grid.addNode(NameConstants.NN_RESPONSIVE_CONFIG, JcrConstants.NT_UNSTRUCTURED);
            Node defaultGridStructure = gridConfig.addNode(DEFAULT_LAYOUT_NAME, JcrConstants.NT_UNSTRUCTURED);
            defaultGridStructure.setProperty(PROP_WIDTH, widths[i]);
            defaultGridStructure.setProperty(PROP_OFFSET, 0);
            node.remove();

            // Move nodes to the grid.
            while (siblings.hasNext()) {
                node = siblings.nextNode();
                javax.jcr.Property prop = node.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
                if (StringUtils.equals(prop.getString(), resourceType)) {
                    break;
                }
                JcrUtil.copy(node, grid, node.getName());
                node.remove();
            }
        }
        // This should be the end column control
        node.remove();
        return parent.getNode(firstNodeName);
    }

    @Activate
    protected void activate(ComponentContext context) throws RepositoryException, ConfigurationException {
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> props = context.getProperties();
        // read service ranking property
        Object ranking = props.get("service.ranking");
        if (ranking != null) {
            try {
                this.ranking = (Integer) ranking;
            } catch (ClassCastException e) {
                // ignore
                logger.warn("Found invalid service.ranking value {}", ranking);
            }
        }

        resourceType = PropertiesUtil.toString(props.get(PROP_RESOURCE_TYPE), PROP_RESOURCE_TYPE_DEFAULT);
        if (StringUtils.isBlank(resourceType)) {
            throw new ConfigurationException(PROP_RESOURCE_TYPE, "Sling Resource Type is required.");
        }

        layout = PropertiesUtil.toString(props.get(PROP_LAYOUT_VALUE), null);
        if (StringUtils.isBlank(layout)) {
            throw new ConfigurationException(PROP_LAYOUT_VALUE, "Layout value property is required.");
        }

        String[] widthsStrings = PropertiesUtil.toStringArray(props.get(PROP_COLUMN_WIDTHS), null);
        if (ArrayUtils.isEmpty(widthsStrings)) {
            throw new ConfigurationException(PROP_COLUMN_WIDTHS, "Column width property is required.");
        } else {
            widths = new long[widthsStrings.length];
            for (int i = 0; i < widthsStrings.length; i++) {
                try {
                    widths[i] = Long.parseLong(widthsStrings[i]);
                } catch (NumberFormatException ex) {
                    throw new ConfigurationException(PROP_COLUMN_WIDTHS, "Column width values must be of type long.");
                }
            }
        }
    }

    @Override
    public int getRanking() {
        return this.ranking;
    }}
