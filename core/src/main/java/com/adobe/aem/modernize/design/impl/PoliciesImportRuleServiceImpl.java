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

package com.adobe.aem.modernize.design.impl;

import com.adobe.aem.modernize.design.PoliciesImportRule;
import com.adobe.aem.modernize.design.PoliciesImportRuleService;
import com.adobe.aem.modernize.design.impl.rules.NodeBasedPoliciesImportRule;
import com.day.cq.commons.jcr.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Component(
    service = { PoliciesImportRuleService.class },
    reference = {
        @Reference(
            name = "rule",
            service = PoliciesImportRule.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            bind = "bindRule",
            unbind = "unbindRule"
        )
    }
)
public class PoliciesImportRuleServiceImpl implements PoliciesImportRuleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoliciesImportRuleService.class);

    /**
     * Relative path to the node containing node-based policy import rules
     */
    private static final String RULES_SEARCH_PATH = "cq/modernize/design/rules";

    /**
     * Keeps track of OSGi services implementing policy import rules
     */
    private List<PoliciesImportRule> rules = Collections.synchronizedList(new LinkedList<>());

    @SuppressWarnings("unused")
    public void bindRule(PoliciesImportRule rule) {
        rules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindRule(PoliciesImportRule rule) {
        rules.remove(rule);
    }

    public List<PoliciesImportRule> getRules(ResourceResolver resolver) throws RepositoryException {
        final List<PoliciesImportRule> rules = new LinkedList<>();

        // Get rules provided as OSGi services (we need to synchronize, since the 'addAll' will iterate over 'rules')
        synchronized (this.rules) {
            rules.addAll(this.rules);
        }
        int jb = rules.size();

        // Get node-based rules
        Resource resource = resolver.getResource(RULES_SEARCH_PATH);
        if (resource != null) {
            Node rulesContainer = resource.adaptTo(Node.class);
            NodeIterator iterator = rulesContainer.getNodes();
            while (iterator.hasNext()) {
                Node nextNode = iterator.nextNode();
                if (isFolder(nextNode)) {
                    // add first level folder rules
                    NodeIterator nodeIterator = nextNode.getNodes();
                    while (nodeIterator.hasNext()) {
                        Node nestedNode = nodeIterator.nextNode();
                        // don't include nested folders
                        if (!isFolder(nestedNode)) {
                            rules.add(new NodeBasedPoliciesImportRule(nestedNode));
                        }
                    }
                } else {
                    // add rules directly at the rules search path
                    rules.add(new NodeBasedPoliciesImportRule(nextNode));
                }
            }
        }

        // sort rules according to their ranking
        Collections.sort(rules, new PoliciesImportRuleServiceImpl.RuleComparator());

        LOGGER.debug("Found {} rules ({} Java-based, {} node-based)", rules.size(), jb, rules.size() - jb);
        if (LOGGER.isDebugEnabled()) {
            for (PoliciesImportRule rule : rules) {
                LOGGER.debug(rule.toString());
            }
        }

        return rules;
    }

//    @Override
    public Set<String> getSlingResourceTypes(ResourceResolver resolver) throws RepositoryException {
        List<PoliciesImportRule> rules = getRules(resolver);

        Set<String> types = new HashSet<>(rules.size());

        for (PoliciesImportRule r : rules) {
            types.addAll(r.getPatternSlingResourceTypes());
        }
        return types;
    }

    private class RuleComparator implements Comparator<PoliciesImportRule> {

        public int compare(PoliciesImportRule rule1, PoliciesImportRule rule2) {
            int ranking1 = rule1.getRanking();
            int ranking2 = rule2.getRanking();
            return Double.compare(ranking1, ranking2);
        }

    }

    private boolean isFolder(Node node) throws RepositoryException {
        String primaryType = node.getPrimaryNodeType().getName();

        return primaryType.equals("sling:Folder")
                || primaryType.equals("sling:OrderedFolder")
                || primaryType.equals(JcrConstants.NT_FOLDER);
    }

}
