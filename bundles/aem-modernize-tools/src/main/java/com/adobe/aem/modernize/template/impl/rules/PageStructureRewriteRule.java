package com.adobe.aem.modernize.template.impl.rules;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.impl.impl.AbstractNodeBasedRewriteRule;
import com.adobe.aem.modernize.template.PageTemplateRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;

/**
 * Rewrites the jcr:content node of a Page.
 * Updates the <code>cq:template</code> reference to the mapped value
 * Removes the <code>cq:designPath</code> property, as it is unnecessary on Editable Templates
 * Creates a root responsive layout component and moves all children nodes (e.g. components) to this container.
 */
public class PageStructureRewriteRule extends AbstractNodeBasedRewriteRule implements PageTemplateRewriteRule {

    private static final String RESPONSIVE_GRID_NODE_NAME = "root";
    private static final String RESPONSIVE_GRID_RES_TYPE = "wcm/foundation/components/responsivegrid";

    public PageStructureRewriteRule(Node ruleNode) {
        super(ruleNode);
    }

    @Override
    protected void doAdditionalApplyTo(Node root, Node copy, Node replacementRules) throws RepositoryException {
        Node responsiveGrid = copy.addNode(RESPONSIVE_GRID_NODE_NAME, JcrConstants.NT_UNSTRUCTURED);
        responsiveGrid.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, RESPONSIVE_GRID_RES_TYPE);

        NodeIterator children = copy.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            if (!RESPONSIVE_GRID_NODE_NAME.equals(child.getName())) {
                // Copy the node to the new location, then remove it.
                JcrUtil.copy(child, responsiveGrid, child.getName());
                child.remove();
            }

        }
        return;
    }
}
