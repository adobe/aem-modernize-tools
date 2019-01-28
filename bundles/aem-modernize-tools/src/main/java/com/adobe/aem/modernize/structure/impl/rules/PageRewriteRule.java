package com.adobe.aem.modernize.structure.impl.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.structure.PageStructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites the jcr:content node of a Page.
 * Updates the <code>cq:template</code> reference to the mapped value
 * Removes the <code>cq:designPath</code> property, as it is unnecessary on Editable Templates
 * Creates a root responsive layout component and moves all children nodes (e.g. components) to this container.
 */
@Service(value = { PageStructureRewriteRule.class, StructureRewriteRule.class })
@Component(

        configurationFactory = true,
        policy = ConfigurationPolicy.REQUIRE,
        metatype = true,
        label="Page Rewrite Rule", description="Rewrites a page template & structure to use new responsive grid layout.")
@Properties({
        @org.apache.felix.scr.annotations.Property(name="service.ranking", intValue = 1)
})
public class PageRewriteRule implements PageStructureRewriteRule {

    private static final Logger logger = LoggerFactory.getLogger(PageRewriteRule.class);

    @org.apache.felix.scr.annotations.Property(label = "Static Template",
            description = "The static template which will be updated by this Page Rewrite Rule")
    private static final String PROP_STATIC_TEMPLATE = "static.template";

    private String staticTemplate;

    @org.apache.felix.scr.annotations.Property(label = "Editable Template",
            description = "The value to update the cq:template with, this should be the new Editable Template.")
    private static final String PROP_EDITABLE_TEMPLATE = "editable.template";

    private String editableTemplate;

    @org.apache.felix.scr.annotations.Property(label = "Sling Resource Type",
            description = "The value to update the sling:resourceType with, this should be the same as the " +
                    "new Editable Template.")
    private static final String PROP_SLING_RESOURCE_TYPE = "sling.resourceType";

    private String slingResourceType;


    @org.apache.felix.scr.annotations.Property(label = "Component Order",
            cardinality = Integer.MAX_VALUE,
            description = "Specify the order of the components in the new responsive grid. " +
                    "Any found and unspecified are moved to the end in arbitrary order.")
    private static final String PROP_ORDER_COMPONENTS = "order.components";

    private String[] componentOrder;

    @org.apache.felix.scr.annotations.Property(label = "Remove Components",
            cardinality = Integer.MAX_VALUE,
            description = "Specify any components that may exist on the static page that can be removed.")
    private static final String PROP_REMOVE_COMPONENTS = "remove.components";

    private String[] componentsToRemove;


    @org.apache.felix.scr.annotations.Property(label = "Rename Components",
            cardinality = Integer.MAX_VALUE,
            description = "Specify new name for components as they are moved into the root responsive grid.")
    private static final String PROP_RENAME_COMPONENTS = "rename.components";

    private Map<String, String> componentRenamed;

    private int ranking = Integer.MAX_VALUE;

    private static final String RESPONSIVE_GRID_NODE_NAME = "root";

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

        List<String> order = Arrays.asList(componentOrder);
        List<String> remove = Arrays.asList(componentsToRemove);

        List<String> names = new ArrayList<>();

        // Remove Design Property.
        if (root.hasProperty(NameConstants.PN_DESIGN_PATH)) {
            root.getProperty(NameConstants.PN_DESIGN_PATH).remove();
        }

        Property template = root.getProperty(NameConstants.NN_TEMPLATE);
        template.setValue(editableTemplate);
        Property resourceType = root.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
        resourceType.setValue(slingResourceType);


        Node responsiveGrid = root.addNode(RESPONSIVE_GRID_NODE_NAME, JcrConstants.NT_UNSTRUCTURED);
        responsiveGrid.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                RewriteUtils.RESPONSIVE_GRID_RES_TYPE);

        NodeIterator children = root.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            if (remove.contains(child.getName())) {
                child.remove();
                continue;
            }
            // Only copy root children first.
            if (!RESPONSIVE_GRID_NODE_NAME.equals(child.getName())) {
                // Copy the node to the new location, then remove it.
                String name = componentRenamed.containsKey(child.getName()) ? componentRenamed.get(child.getName()) :
                        child.getName();
                JcrUtil.copy(child, responsiveGrid, name);
                names.add(name);
                child.remove();
            }
        }
        List<String> copy = new ArrayList<>(order);

        Collections.reverse(copy);

        // Fix the order based on passed information.
        // Place last->first, as orderBefore is always in this order.
        Iterator<String> oit = copy.iterator();
        String after = null;
        while (oit.hasNext()) {
            String before = oit.next();
            responsiveGrid.orderBefore(before, after);
            names.remove(before);
            after = before;
        }
         // Move all unordered ones to the end, in original order.
        Iterator<String> nit = names.iterator();
        while (nit.hasNext()) {
            responsiveGrid.orderBefore(nit.next(), null);
        }

        return root;
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

        staticTemplate = PropertiesUtil.toString(props.get(PROP_STATIC_TEMPLATE), null);
        if (StringUtils.isBlank(staticTemplate)) {
            throw new ConfigurationException(PROP_STATIC_TEMPLATE, "Static template is required.");
        }

        editableTemplate = PropertiesUtil.toString(props.get(PROP_EDITABLE_TEMPLATE), null);
        if (StringUtils.isBlank(editableTemplate)) {
            throw new ConfigurationException(PROP_EDITABLE_TEMPLATE, "Editable template is required.");
        }

        slingResourceType = PropertiesUtil.toString(props.get(PROP_SLING_RESOURCE_TYPE), null);
        if (StringUtils.isBlank(slingResourceType)) {
            throw new ConfigurationException(PROP_SLING_RESOURCE_TYPE, "Sling Resource Type is required.");
        }

        componentOrder = PropertiesUtil.toStringArray(props.get(PROP_ORDER_COMPONENTS), new String[] {});

        componentsToRemove = PropertiesUtil.toStringArray(props.get(PROP_REMOVE_COMPONENTS), new String[] {});

        componentRenamed =RewriteUtils.toMap(
                PropertiesUtil.toStringArray(props.get(PROP_RENAME_COMPONENTS), new String[] {}), ":"
        );
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
