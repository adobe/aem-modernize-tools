/*
 *  (c) 2014 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.component.impl.rules;

import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.impl.impl.AbstractNodeBasedRewriteRule;

/**
 * An rule that rewrites a tree based on a given node structure. The node structure follows all
 * of the rules as found in {@link AbstractNodeBasedRewriteRule}.
 */
public class NodeBasedComponentRewriteRule extends AbstractNodeBasedRewriteRule implements ComponentRewriteRule {

    public NodeBasedComponentRewriteRule(Node ruleNode) {
        super(ruleNode);
    }

    @Override
    protected void doAdditionalApplyTo(Node root, Node copy, Node replacementNode) {
        // No custom logic here.
        return;
    }

    public Set<String> getSlingResourceTypes() throws RepositoryException {
        Set<String> types = new HashSet<>();

        if (!getRuleNode().hasNode("patterns")) {
            return types;
        }

        NodeIterator patterns = getRuleNode().getNode("patterns").getNodes();
        while (patterns.hasNext()) {
            Node pnode = patterns.nextNode();
            if (pnode.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
                types.add(pnode.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());
            }
        }
        return types;
    }

}
