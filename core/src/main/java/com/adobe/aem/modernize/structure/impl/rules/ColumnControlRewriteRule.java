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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites a Column Control component into multiple Responsive Grid layout components. Each column in the
 * original control will become a grid instance with all of the appropriately nested components.
 *
 * The layout is mapped to column spans within the responsive grid.
 */
@Component(
    service = { StructureRewriteRule.class },
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = {
        "service.ranking=3"
    }
)
@Designate(ocd = ColumnControlRewriteRule.Config.class, factory = true)
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

    private String resourceType = PROP_RESOURCE_TYPE_DEFAULT;
    private String layout;
    private long[] widths;


    @ObjectClassDefinition(
        // The name and description of the @ObjectClassDefinition define the name/description that show in the OSGi Console for this Component.
        name = "Column Control Rewrite Rule",
        description = "Rewrites Column control components to responsive grid replacements."
    )
    @interface Config {
        @AttributeDefinition(
            name = "Column Control ResourceType",
            description =  "The sling:resourceType of this column control, leave blank to use Foundation component. (some customers extend/create their own)"
        )
        String sling_resourceType() default PROP_RESOURCE_TYPE_DEFAULT;

        @AttributeDefinition(
            name = "Layout Property Value",
            description =  "The value of the `layout` property on the primary column control component."
        )
        String layout_value();

        @AttributeDefinition(
            name = "Column Widths",
            description =  "An array of widths for each of the columns in target grid. (e.g. 50%/50% column control is [6,6] in target grid. 75%/25% => [8,4])",
            cardinality = Integer.MAX_VALUE
        )
        String[] column_widths();
    }

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
     * @param finalNodes list of nodes that should not be processed
     * @return updated Node
     * @throws RewriteException when an error occurs during the rewrite operation
     * @throws RepositoryException when any repository operation error occurs
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
    @Modified
    protected void activate(ComponentContext context, Config config) throws RepositoryException, ConfigurationException {
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> props = context.getProperties();
        // read service ranking property
        this.ranking = PropertiesUtil.toInteger(props.get("service.ranking"), this.ranking);

        resourceType = config.sling_resourceType();
        if (StringUtils.isBlank(resourceType)) {
            throw new ConfigurationException("sling.resourceType", "Sling Resource Type is required.");
        }

        layout = config.layout_value();
        if (StringUtils.isBlank(layout)) {
            throw new ConfigurationException("layout.value", "Layout value property is required.");
        }

        String[] widthsStrings = config.column_widths();
        if (ArrayUtils.isEmpty(widthsStrings)) {
            throw new ConfigurationException("column.widths", "Column width property is required.");
        } else {
            widths = new long[widthsStrings.length];
            for (int i = 0; i < widthsStrings.length; i++) {
                try {
                    widths[i] = Long.parseLong(widthsStrings[i]);
                } catch (NumberFormatException ex) {
                    throw new ConfigurationException("column.widths", "Column width values must be of type long.");
                }
            }
        }
    }

    @Override
    public int getRanking() {
        return this.ranking;
    }}
