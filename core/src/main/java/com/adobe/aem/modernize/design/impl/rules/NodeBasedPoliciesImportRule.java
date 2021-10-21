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
package com.adobe.aem.modernize.design.impl.rules;

import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.policy.PolicyImportRule;
import com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule;

public class NodeBasedPoliciesImportRule extends NodeBasedRewriteRule implements PolicyImportRule {

    public NodeBasedPoliciesImportRule(Node ruleNode) throws RepositoryException {
        super(ruleNode);
    }


    public Set<String> getPatternSlingResourceTypes() throws RepositoryException {
        Set<String> types = new HashSet<>();

        if (!rule.hasNode("patterns")) {
            return types;
        }

        NodeIterator patterns = rule.getNode("patterns").getNodes();
        while (patterns.hasNext()) {
            Node pnode = patterns.nextNode();
            if (pnode.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
                types.add(pnode.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());
            }
        }
        return types;
    }


    public String getReplacementSlingResourceType() throws RepositoryException {
        Node replacement = rule.getNode("replacement");
        if (replacement.hasNodes()) {
            replacement = replacement.getNodes().nextNode();
            if (replacement.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
                return replacement.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();

            }
        }
        return null;
    }
}
