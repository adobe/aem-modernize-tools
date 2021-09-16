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

package com.adobe.aem.modernize.component.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.component.impl.rules.NodeBasedComponentRewriteRule;
import com.day.cq.commons.jcr.JcrConstants;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = { ComponentRewriteRuleService.class },
    reference = {
        @Reference(
            name = "rule",
            service = ComponentRewriteRule.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            bind = "bindRule",
            unbind = "unbindRule"
        )
    }
)
public class ComponentRewriteRuleServiceImpl implements ComponentRewriteRuleService {

    private Logger logger = LoggerFactory.getLogger(ComponentRewriteRuleServiceImpl.class);
    /**
     * Relative path to the node containing node-based component rewrite rules
     */
    public static final String RULES_SEARCH_PATH = "cq/modernize/component/rules";

    /**
     * Keeps track of OSGi services implementing component rewrite rules
     */
    private List<ComponentRewriteRule> rules = Collections.synchronizedList(new LinkedList<>());

    @SuppressWarnings("unused")
    public void bindRule(ComponentRewriteRule rule) {
        rules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindRule(ComponentRewriteRule rule) {
        rules.remove(rule);
    }

    @Override
    public void apply(@NotNull Resource resource, @NotNull String[] rules, boolean deep) {
    }

    public List<ComponentRewriteRule> getRules(ResourceResolver resolver) throws RepositoryException {
        final List<ComponentRewriteRule> rules = new LinkedList<>();

        // 1) rules provided as OSGi services
        // (we need to synchronize, since the 'addAll' will iterate over 'rules')
        synchronized (this.rules) {
            rules.addAll(this.rules);
        }
        int jb = rules.size();

        // 2) node-based rules
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
                            rules.add(new NodeBasedComponentRewriteRule(nestedNode));
                        }
                    }
                } else {
                    // add rules directly at the rules search path
                    rules.add(new NodeBasedComponentRewriteRule(nextNode));
                }
            }
        }

        // sort rules according to their ranking
        Collections.sort(rules, new ComponentRewriteRuleServiceImpl.RuleComparator());

        logger.debug("Found {} rules ({} Java-based, {} node-based)", rules.size(), jb, rules.size() - jb);
        if (logger.isDebugEnabled()) {
            for (ComponentRewriteRule rule : rules) {
                logger.debug(rule.toString());
            }
        }

        return rules;
    }

//    @Override
    public Set<String> getSlingResourceTypes(ResourceResolver resolver) throws RepositoryException {
        List<ComponentRewriteRule> rules = getRules(resolver);

        Set<String> types = new HashSet<>(rules.size());

        for (ComponentRewriteRule r : rules) {
            types.addAll(r.getSlingResourceTypes());
        }
        return types;
    }

    private class RuleComparator implements Comparator<ComponentRewriteRule> {

        public int compare(ComponentRewriteRule rule1, ComponentRewriteRule rule2) {
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
