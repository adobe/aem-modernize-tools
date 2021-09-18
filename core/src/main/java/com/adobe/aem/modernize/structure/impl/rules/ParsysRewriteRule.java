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

import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites Components of type <code>foundation/components/parsys</code> to the Responsive Layout container.
 */
@Component(
    service = { StructureRewriteRule.class },
    property = {
        "service.ranking=2"
    }
)
public class ParsysRewriteRule implements StructureRewriteRule {

    private static final Logger logger = LoggerFactory.getLogger(ParsysRewriteRule.class);

    private static final String PARSYS_RESOURCE_TYPE = "foundation/components/parsys";

    private int ranking = Integer.MAX_VALUE;
    private String id = this.getClass().getName();

    @Override
    public String getId() {
        return this.id;
    }

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
        this.ranking = PropertiesUtil.toInteger(props.get("service.ranking"), Integer.MAX_VALUE);
        this.id = PropertiesUtil.toString(props.get("service.pid"), this.id);
    }
}
