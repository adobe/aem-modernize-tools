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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.structure.PageStructureRewriteRule;
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
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites the jcr:content node of a Page.
 * Updates the <code>cq:template</code> reference to the mapped value
 * Removes the <code>cq:designPath</code> property, as it is unnecessary on Editable Templates
 * Creates a root responsive layout component and moves all children nodes (e.g. components) to this container.
 */
@Component(
        service = { PageStructureRewriteRule.class, StructureRewriteRule.class },
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
            "service.ranking=1"
        }
)
@Designate(ocd = PageRewriteRule.Config.class, factory = true)
public class PageRewriteRule implements PageStructureRewriteRule {

    private static final Logger logger = LoggerFactory.getLogger(PageRewriteRule.class);

    private String staticTemplate;
    private String editableTemplate;
    private String slingResourceType;
    private Map<String, List<String>> componentOrdering = new HashMap<>();
    private String[] componentsToRemove;
    private Map<String, String> componentRenamed;

    private List<String> nested = new ArrayList<>();
    private int ranking = Integer.MAX_VALUE;
    private static final String RESPONSIVE_GRID_NODE_NAME = "root";

    @ObjectClassDefinition(
        name="Page Rewrite Rule",
        description="Rewrites a page template & structure to use new responsive grid layout."
    )
    @interface Config {
        @AttributeDefinition(
            name = "Static Template",
            description = "The static template which will be updated by this Page Rewrite Rule"
        )
        String static_template();

        @AttributeDefinition(
            name = "Editable Template",
            description = "The value to update the cq:template with, this should be the new Editable Template.")
        String editable_template();

        @AttributeDefinition(
            name = "Sling Resource Type",
            description = "The value to update the sling:resourceType with, this should be the same as the " +
                "new Editable Template.")
        String sling_resourceType();

        @AttributeDefinition(
            name = "Component Order",
            description = "Specify the order of the components in the new responsive grid. " +
                "Any found and unspecified are moved to the end in arbitrary order.",
            cardinality = Integer.MAX_VALUE
        )
        String[] order_components();

        @AttributeDefinition(
            name = "Remove Components",
            description = "Specify any components that may exist on the static page that can be removed.",
            cardinality = Integer.MAX_VALUE
        )
        String[] remove_components();

        @AttributeDefinition(
            name = "Rename Components",
            description = "Specify new name for components as they are moved into the root responsive grid.",
            cardinality = Integer.MAX_VALUE
        )
        String[] rename_components();

    }

    @Override
    public boolean matches(Node root) throws RepositoryException {
        if (!root.hasProperty(NameConstants.NN_TEMPLATE)) {
            return false;
        }
        String template = root.getProperty(NameConstants.NN_TEMPLATE).getString();
        return StringUtils.equals(staticTemplate, template);
    }

    @Override
    public Node applyTo(Node root, Set<Node> finalNodes) throws RepositoryException {


        // Remove Design Property.
        if (root.hasProperty(NameConstants.PN_DESIGN_PATH)) {
            root.getProperty(NameConstants.PN_DESIGN_PATH).remove();
        }

        // Remove specified nodes
        List<String> remove = Arrays.asList(componentsToRemove);
        NodeIterator it = root.getNodes();
        while (it.hasNext()) {
            Node child = it.nextNode();
            if (remove.contains(child.getName())) {
                child.remove();
            }
        }

        Property template = root.getProperty(NameConstants.NN_TEMPLATE);
        template.setValue(editableTemplate);
        Property resourceType = root.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
        resourceType.setValue(slingResourceType);


        Node responsiveGrid = root.addNode(RESPONSIVE_GRID_NODE_NAME, JcrConstants.NT_UNSTRUCTURED);
        responsiveGrid.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                RewriteUtils.RESPONSIVE_GRID_RES_TYPE);

        // First Copy the Root children over
        List<String> names = moveStructure(root, responsiveGrid, nested);
        // Copy & Create any nested
        for (String key : componentOrdering.keySet()) {

            // Ignore root, it'll be handled after everything is copied.
            if (RESPONSIVE_GRID_NODE_NAME.equals(key)) {
                continue;
            }

            if (!responsiveGrid.hasNode(key)) {
                Node child = responsiveGrid.addNode(key, JcrConstants.NT_UNSTRUCTURED);
                child.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, RewriteUtils.RESPONSIVE_GRID_RES_TYPE);
            }
            Node child = responsiveGrid.getNode(key);
            List<String> cn = moveStructure(root, child, names);
            reorderChildren(cn, componentOrdering.get(key), child);
        }

        reorderChildren(names, componentOrdering.get(RESPONSIVE_GRID_NODE_NAME), responsiveGrid);

        return root;
    }

    private List<String> moveStructure(Node source, Node target, List<String> ignored) throws RepositoryException {
        List<String> names = new ArrayList<>();

        NodeIterator children = source.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            String name = componentRenamed.containsKey(child.getName()) ? componentRenamed.get(child.getName()) :
                    child.getName();
            if (RESPONSIVE_GRID_NODE_NAME.equals(name) || ignored.contains(name)) {
                continue;
            }

            name = name.replaceFirst(target.getName() + "/", "");
            // Copy the node to the new location, then remove it.
            JcrUtil.copy(child, target, name);
            names.add(name);
            child.remove();

        }
        return names;
    }

    /**
     * Reorder children in the specific order, if the child doesn't exist, create it (assumed to be responsive grid)
     * @param names
     * @param parent
     * @throws RepositoryException
     */
    private void reorderChildren(List<String> names, List<String> order, Node parent) throws RepositoryException {
        // Order the direct children.
        List<String> copy = new ArrayList<>(order);
        Collections.reverse(copy);

        // Fix the order based on passed information.
        // Place last->first, as orderBefore is always in this order.
        Iterator<String> oit = copy.iterator();
        String after = null;
        while (oit.hasNext()) {
            String before = oit.next();
            parent.orderBefore(before, after);
            names.remove(before);
            after = before;
        }
        // Move all unordered ones to the end, in original order.
        Iterator<String> nit = names.iterator();
        while (nit.hasNext()) {
            parent.orderBefore(nit.next(), null);
        }
    }

    @Activate
    @Modified
    protected void activate(ComponentContext context, Config config) throws RepositoryException, ConfigurationException {
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> props = context.getProperties();
        // read service ranking property
        this.ranking = PropertiesUtil.toInteger(props.get("service.ranking"), this.ranking);
        this.ranking = Converters.standardConverter().convert(props.get("service.ranking")).defaultValue(Integer.MAX_VALUE).to(Integer.class);

        staticTemplate = config.static_template();
        if (StringUtils.isBlank(staticTemplate)) {
            throw new ConfigurationException("static.template", "Static template is required.");
        }

        editableTemplate = config.editable_template();
        if (StringUtils.isBlank(editableTemplate)) {
            throw new ConfigurationException("editable.template", "Editable template is required.");
        }

        slingResourceType = config.sling_resourceType();
        if (StringUtils.isBlank(slingResourceType)) {
            throw new ConfigurationException("sling.resourceType", "Sling Resource Type is required.");
        }



        String[] orders = config.order_components();
        for (String o : orders) {
            String[] tmp = StringUtils.split(o, ":", 2);
            if (tmp.length < 1) {
                continue;
            }
            if (tmp.length == 2) {
                nested.add(tmp[1]);
            }
            String key = tmp.length == 2 ? tmp[0] : RESPONSIVE_GRID_NODE_NAME;
            String value = tmp.length == 2 ? tmp[1] : tmp[0];
            if (componentOrdering.get(key) == null) {
                componentOrdering.put(key, new ArrayList<>());
            }
            componentOrdering.get(key).add(value);
        }

        componentsToRemove = config.remove_components();

        componentRenamed = RewriteUtils.toMap(config.rename_components(), ":");

        // Add any renamed component that contains a path separator
        for (String val : componentRenamed.values()) {
            if (val.contains("/")) {
                nested.add(val);
            }
        }
    }

    @Override
    public String getStaticTemplate() {
        return staticTemplate;
    }

    @Override
    public int getRanking() {
        return this.ranking;
    }
}
