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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.AbstractRewriteRuleService;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import org.jetbrains.annotations.NotNull;
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
        )
    }
)
public class StructureRewriteRuleServiceImpl extends AbstractRewriteRuleService<StructureRewriteRule> implements StructureRewriteRuleService {

  private final Logger logger = LoggerFactory.getLogger(StructureRewriteRuleServiceImpl.class);

  /**
   * Keeps track of OSGi services implementing structure rewrite rules
   */
  private final RankedServices<StructureRewriteRule> rules = new RankedServices<>(Order.ASCENDING);

  @SuppressWarnings("unused")
  public void bindRule(StructureRewriteRule rule, Map<String, Object> properties) {
    rules.bind(rule, properties);
  }

  @SuppressWarnings("unused")
  public void unbindRule(StructureRewriteRule rule, Map<String, Object> properties) {
    rules.unbind(rule, properties);
  }

  @Override
  public void apply(@NotNull Page page, @NotNull Set<String> rules) throws RewriteException {
    Resource pageContent = page.getContentResource();
    if (pageContent == null) {
      logger.warn("Request to rewrite a page with no content: {}", page.getPath());
      return;
    }
    ResourceResolver rr = pageContent.getResourceResolver();
    List<RewriteRule> rewrites = create(rr, rules);

    Node node = pageContent.adaptTo(Node.class);
    try {
      for (RewriteRule rule : rewrites) {
        if (rule.matches(node)) {
          rule.applyTo(node, new HashSet<>());
        }
      }
    } catch (RepositoryException e) {
      logger.error("Error occurred while trying to perform a rewrite operation.", e);
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
  }

  @NotNull
  @Override
  protected List<String> getSearchPaths() {
    return Collections.emptyList();
  }

  @Override
  protected @NotNull List<StructureRewriteRule> getServiceRules() {
    return Collections.unmodifiableList(rules.getList());
  }
}
