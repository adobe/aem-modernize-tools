package com.adobe.aem.modernize.structure.impl;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

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

  @NotNull
  @Override
  protected List<String> getSearchPaths() {
    return Collections.emptyList();
  }

  
  @Override
  @Deprecated(since = "2.1.0")
  public void apply(@NotNull Page page, @NotNull Set<String> rules) throws RewriteException {
    apply(page.adaptTo(Resource.class), rules);
  }

  @Override
  public boolean apply(@NotNull Resource resource, @NotNull Set<String> rules) throws RewriteException {
    
    Page page = resource.adaptTo(Page.class);
    if (page == null) {
      return false;
    }
    Resource pageContent = page.getContentResource();
    if (pageContent == null) {
      logger.warn("Request to rewrite a page with no content: {}", page.getPath());
      return false;
    }
    ResourceResolver rr = pageContent.getResourceResolver();
    List<RewriteRule> rewrites = create(rr, rules);

    Node node = pageContent.adaptTo(Node.class);
    boolean applied = false;
    try {
      for (RewriteRule rule : rewrites) {
        if (rule.matches(node)) {
          rule.applyTo(node, new HashSet<>());
          applied = true;
        }
      }
    } catch (RepositoryException e) {
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
    return applied;
  }

  @SuppressWarnings("unused")
  public void bindRule(StructureRewriteRule rule, Map<String, Object> properties) {
    rules.bind(rule, properties);
    ruleMap.put(rule.getId(), rule);
  }

  @SuppressWarnings("unused")
  public void unbindRule(StructureRewriteRule rule, Map<String, Object> properties) {
    rules.unbind(rule, properties);
    ruleMap.remove(rule.getId());
  }
}
