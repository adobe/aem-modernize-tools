package com.adobe.aem.modernize.structure.impl.rules;

import java.util.Dictionary;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites Components of type <code>foundation/components/parsys</code> to the Responsive Layout container.
 */
@Component
@Service
@Properties({
        @Property(name="service.ranking", intValue = 2)
})
public class ParsysRewriteRule implements StructureRewriteRule {

    private static final Logger logger = LoggerFactory.getLogger(ParsysRewriteRule.class);

    private static final String PARSYS_RESOURCE_TYPE = "foundation/components/parsys";

    private int ranking = Integer.MAX_VALUE;

    @Override
    public boolean matches(Node root) throws RepositoryException {
        if (!root.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
            return false;
        }
        try {
            String resourceType = root.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();
            return PARSYS_RESOURCE_TYPE.equals(resourceType);
        } catch (RepositoryException ex) {
            logger.error("Error looking up sling:resourceType: {}", ex.getMessage());
        }

        return false;
    }

    @Override
    public Node applyTo(Node root, Set<Node> finalNodes) throws RewriteException, RepositoryException {

        String name = root.getName();
        Node parent = root.getParent();

        String orderBefore = null;
        // Find the order
        NodeIterator siblings = parent.getNodes();
        while (siblings.hasNext()) {
            Node sib = siblings.nextNode();
            if (sib.getName().equals(name)) {
                orderBefore = siblings.hasNext() ? siblings.nextNode().getName() : null;
                break;
            }
        }
        RewriteUtils.rename(root);
        Node grid = parent.addNode(name, root.getPrimaryNodeType().getName());
        grid.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, RewriteUtils.RESPONSIVE_GRID_RES_TYPE);
        parent.orderBefore(name, orderBefore);
        NodeIterator children = root.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            JcrUtil.copy(child, grid, child.getName());
        }
        root.remove();
        return grid;
    }


    @Activate
    protected void activate(ComponentContext context) throws RepositoryException {
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
    }

    @Override
    public int getRanking() {
        return this.ranking;
    }
}
