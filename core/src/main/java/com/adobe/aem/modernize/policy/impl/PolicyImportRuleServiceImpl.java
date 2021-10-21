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

package com.adobe.aem.modernize.policy.impl;

import com.adobe.aem.modernize.policy.PolicyImportRule;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;

import com.day.cq.search.QueryBuilder;
import com.day.cq.wcm.api.designer.Design;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

@Component(
    service = { PolicyImportRuleService.class },
    reference = {
        @Reference(
            name = "rule",
            service = PolicyImportRule.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            bind = "bindRule",
            unbind = "unbindRule"
        )
    }
)
public class PolicyImportRuleServiceImpl implements PolicyImportRuleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyImportRuleService.class);

    /**
     * Keeps track of OSGi services implementing component rewrite rules
     */
    private final RankedServices<PolicyImportRule> rules = new RankedServices<>(Order.ASCENDING);
    private Config config;

    @Reference
    private QueryBuilder queryBuilder;


    @SuppressWarnings("unused")
    public void bindRule(PolicyImportRule rule, Map<String, Object> properties) {
        rules.bind(rule, properties);
    }

    @SuppressWarnings("unused")
    public void unbindRule(PolicyImportRule rule, Map<String, Object> properties) {
        rules.unbind(rule, properties);
    }

    @Override
    public void apply(@NotNull Design design, @NotNull Set<String> rules, boolean deep, boolean overwrite) {

    }

    @Override
    public @NotNull Set<String> findResources(Resource resource) {
        return null;
    }

    @Override
    public @NotNull Set<RewriteRule> listRules(ResourceResolver resourceResolver, String... slingResourceType) {
        return null;
    }

    @Activate
    @Modified
    protected void activate(PolicyImportRuleServiceImpl.Config config) {
        this.config = config;
    }

    @ObjectClassDefinition(
        name = "Policy Import Rule Service",
        description = "Manages operations for performing policy-level import for Modernization tasks."
    )
    @interface Config {
        @AttributeDefinition(
            name = "Policy Rule Paths",
            description = "List of paths to find node-based Policy Import Rules",
            cardinality = Integer.MAX_VALUE
        )
        String[] search_paths();
    }
}
