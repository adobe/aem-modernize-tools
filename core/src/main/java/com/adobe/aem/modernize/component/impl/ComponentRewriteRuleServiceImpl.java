package com.adobe.aem.modernize.component.impl;

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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.AbstractRewriteRuleService;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
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
@Designate(ocd = ComponentRewriteRuleServiceImpl.Config.class)
public class ComponentRewriteRuleServiceImpl extends AbstractRewriteRuleService<ComponentRewriteRule> implements ComponentRewriteRuleService {

  private static final Logger logger = LoggerFactory.getLogger(ComponentRewriteRuleServiceImpl.class);

  private Config config;

  @NotNull
  @Override
  protected List<String> getSearchPaths() {
    return Arrays.asList(config.search_paths());
  }

  @Override
  @Deprecated(since = "2.1.0")
  public void apply(@NotNull Resource resource, @NotNull Set<String> rules, boolean deep) throws RewriteException {
    ResourceResolver rr = resource.getResourceResolver();

    try {
      if (deep) {
        List<RewriteRule> rewrites = create(rr, rules);
        Node node = resource.adaptTo(Node.class);
        ComponentTreeRewriter.rewrite(node, rewrites);
      } else {
        apply(resource, rules);
      }
    } catch (RepositoryException e) {
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
  }

  @Override
  public boolean apply(@NotNull Resource resource, @NotNull Set<String> rules) throws RewriteException {
    ResourceResolver rr = resource.getResourceResolver();
    List<RewriteRule> rewrites = create(rr, rules);
    Node node = resource.adaptTo(Node.class);
    boolean success = false;

    try {
      String nodeName = node.getName();
      String nodePath = node.getPath();
      String prevName = null;
      Node parent = node.getParent();
      boolean isOrdered = parent.getPrimaryNodeType().hasOrderableChildNodes();
      if (isOrdered) {
        prevName = findPrevName(nodeName, parent);
      }
      for (RewriteRule rule : rewrites) {
        if (rule.matches(node)) {
          node = rule.applyTo(node, new HashSet<>());
          success = true;
        }
      }

      // Only order if node wasn't removed or modified
      if (node != null && nodePath.equals(node.getPath()) && isOrdered) {
        orderParent(nodeName, prevName, parent);
      }
    } catch (RepositoryException e) {
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
    return success;
  }

  private String findPrevName(String nodeName, Node parent) throws RepositoryException {
    String prevName = null;
    // Need to figure out where in the parent's order we are.
    NodeIterator siblings = parent.getNodes();
    while (siblings.hasNext()) {
      Node sibling = siblings.nextNode();
      // Stop when we find ourself in list. Prev will either be set, or not.
      if (sibling.getName().equals(nodeName)) {
        break;
      }
      prevName = sibling.getName();
    }
    return prevName;
  }

  private void orderParent(String nodeName, String prevName, Node parent) throws RepositoryException {
    // Previous not set - we should be first in the order - if previous and first item in list, we're the only child.
    if (prevName == null) {
      String nextName = parent.getNodes().nextNode().getName();
      if (!nextName.equals(nodeName)) {
        parent.orderBefore(nodeName, nextName);
      }
    } else {
      NodeIterator siblings = parent.getNodes();
      String siblingName = siblings.nextNode().getName();
      while (!siblingName.equals(prevName)) {
        // There has to be a better way to skip through a parent's children nodes.
        siblingName = siblings.nextNode().getName();
      }
      siblingName = siblings.nextNode().getName();
      parent.orderBefore(nodeName, siblingName);
    }
  }

  @SuppressWarnings("unused")
  public void bindRule(ComponentRewriteRule rule, Map<String, Object> properties) {
    rules.bind(rule, properties);
    ruleMap.put(rule.getId(), rule);
  }

  @SuppressWarnings("unused")
  public void unbindRule(ComponentRewriteRule rule, Map<String, Object> properties) {
    rules.unbind(rule, properties);
    ruleMap.remove(rule.getId());
  }

  @Activate
  @Modified
  @SuppressWarnings("unused")
  protected void activate(Config config) {
    this.config = config;
  }

  @ObjectClassDefinition(
      name = "AEM Modernize Tools - Component Rewrite Rule Service",
      description = "Manages operations for performing component-level rewrites for Modernization tasks."
  )
  @interface Config {
    @AttributeDefinition(
        name = "Component Rule Paths",
        description = "List of paths to find node-based Component Rewrite Rules",
        cardinality = Integer.MAX_VALUE
    )
    String[] search_paths();
  }

}
