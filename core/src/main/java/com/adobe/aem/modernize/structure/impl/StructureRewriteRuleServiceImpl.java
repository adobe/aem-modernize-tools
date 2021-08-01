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

package com.adobe.aem.modernize.structure.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.structure.PageStructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = { StructureRewriteRuleService.class },
    immediate = true,
    reference = {
        @Reference(
            name = "rule",
            service = StructureRewriteRule.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            bind = "bindRule",
            unbind = "unbindRule"
        ),
        @Reference(
            name = "pageRule",
            service = PageStructureRewriteRule.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            bind = "bindPageRule",
            unbind = "unbindPageRule"
        )
    }
)
public class StructureRewriteRuleServiceImpl implements StructureRewriteRuleService {

    private Logger logger = LoggerFactory.getLogger(StructureRewriteRuleServiceImpl.class);

    /**
     * Keeps track of OSGi services implementing structure rewrite rules
     */

    private List<StructureRewriteRule> rules = Collections.synchronizedList(new LinkedList<>());

    /**
     * Keeps track of OSGi services implementing structure rewrite rules
     */
    private List<PageStructureRewriteRule> pageRules = Collections.synchronizedList(new LinkedList<>());


    @SuppressWarnings("unused")
    public void bindRule(StructureRewriteRule rule) {
        rules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindRule(StructureRewriteRule rule) {
        rules.remove(rule);
    }

    @SuppressWarnings("unused")
    public void bindPageRule(PageStructureRewriteRule rule) {
        pageRules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindPageRule(PageStructureRewriteRule rule) {
        pageRules.remove(rule);
    }

    @Override
    public Set<String> getTemplates() {
        Set<String> templates = new HashSet<>(pageRules.size());
        for (PageStructureRewriteRule r : pageRules) {
            templates.add(r.getStaticTemplate());
        }
        return templates;
    }

    @Override
    public List<StructureRewriteRule> getRules(ResourceResolver resolver) {
        List<StructureRewriteRule> rulesCopy = new LinkedList<>();
        Iterator<StructureRewriteRule> it = rules.iterator();
        while (it.hasNext()) {
            rulesCopy.add(it.next());
        }

        return rulesCopy;
    }
}
