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
        if (node == null) {
          throw new RewriteException("Failed to adapt resource to Node: " + resource.getPath());
        }
        // Use the correct method name from ComponentTreeRewriter
        ComponentTreeRewriter.process(node, rewrites);
      } else {
        apply(resource, rules);
      }
    } catch (RepositoryException e) {
      throw new RewriteException("Repository exception while performing rewrite operation on " + resource.getPath(), e);
    }
  }

  @Override
  public boolean apply(@NotNull Resource resource, @NotNull Set<String> rules) throws RewriteException {
    ResourceResolver rr = resource.getResourceResolver();
    List<RewriteRule> rewrites = create(rr, rules);
    Node node = resource.adaptTo(Node.class);
    boolean success = false;

    if (node == null) {
      throw new RewriteException("Failed to adapt resource to Node: " + resource.getPath());
    }

    try {
      // Obtain ordering information
      NodeOrderInfo orderInfo = NodeOrderInfo.create(node);

      // Apply rules
      for (RewriteRule rule : rewrites) {
        if (rule.matches(node)) {
          node = rule.applyTo(node, new HashSet<>());
          success = true;
        }
      }

      // Apply node ordering if needed
      if (node != null && orderInfo.isOrdered()) {
        orderInfo.applyOrdering(node);
      }

    } catch (RepositoryException e) {
      throw new RewriteException("Repository exception while performing rewrite operation on " + resource.getPath(), e);
    }

    return success;
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

  /**
   * Helper class to encapsulate node ordering information and behavior.
   * This uses the polymorphism pattern to handle different ordering scenarios.
   */
  private static abstract class NodeOrderInfo {

    /**
     * Factory method to create the appropriate NodeOrderInfo implementation
     */
    public static NodeOrderInfo create(Node node) throws RepositoryException {
      String nodeName = node.getName();
      Node parent = node.getParent();
      boolean isOrdered = parent.getPrimaryNodeType().hasOrderableChildNodes();

      if (!isOrdered) {
        return new NonOrderableNodeInfo();
      }

      String previousNodeName = findPreviousNodeName(nodeName, parent);
      if (previousNodeName == null) {
        return new FirstNodeOrderInfo(nodeName, parent);
      } else {
        return new MiddleNodeOrderInfo(nodeName, previousNodeName, parent);
      }
    }

    /**
     * Find the name of the node that comes before the specified node
     */
    private static String findPreviousNodeName(String nodeName, Node parent) throws RepositoryException {
      String prevName = null;
      NodeIterator siblings = parent.getNodes();

      while (siblings.hasNext()) {
        Node sibling = siblings.nextNode();
        if (sibling.getName().equals(nodeName)) {
          break;
        }
        prevName = sibling.getName();
      }

      return prevName;
    }

    /**
     * Apply ordering to the node
     */
    public abstract void applyOrdering(Node node) throws RepositoryException;

    /**
     * Check if the node has orderable parent
     */
    public abstract boolean isOrdered();
  }

  /**
   * Implementation for nodes that don't have orderable parents
   */
  private static class NonOrderableNodeInfo extends NodeOrderInfo {
    @Override
    public void applyOrdering(Node node) {
      // Do nothing - parent doesn't support ordering
    }

    @Override
    public boolean isOrdered() {
      return false;
    }
  }

  /**
   * Implementation for nodes that should be the first among siblings
   */
  private static class FirstNodeOrderInfo extends NodeOrderInfo {
    private final String nodeName;
    private final Node parent;

    public FirstNodeOrderInfo(String nodeName, Node parent) {
      this.nodeName = nodeName;
      this.parent = parent;
    }

    @Override
    public void applyOrdering(Node node) throws RepositoryException {
      NodeIterator siblings = parent.getNodes();
      if (siblings.hasNext()) {
        String firstNodeName = siblings.nextNode().getName();
        if (!firstNodeName.equals(nodeName)) {
          parent.orderBefore(nodeName, firstNodeName);
        }
      }
    }

    @Override
    public boolean isOrdered() {
      return true;
    }
  }

  /**
   * Implementation for nodes that should be placed after a specific sibling
   */
  private static class MiddleNodeOrderInfo extends NodeOrderInfo {
    private final String nodeName;
    private final String previousNodeName;
    private final Node parent;

    public MiddleNodeOrderInfo(String nodeName, String previousNodeName, Node parent) {
      this.nodeName = nodeName;
      this.previousNodeName = previousNodeName;
      this.parent = parent;
    }

    @Override
    public void applyOrdering(Node node) throws RepositoryException {
      NodeIterator siblings = parent.getNodes();
      String siblingName = siblings.nextNode().getName();

      // Find the previous node in the iterator
      while (!siblingName.equals(previousNodeName) && siblings.hasNext()) {
        siblingName = siblings.nextNode().getName();
      }

      // Get the next node's name (the one that should come after our previous)
      if (siblings.hasNext()) {
        siblingName = siblings.nextNode().getName();
        parent.orderBefore(nodeName, siblingName);
      } else {
        // If we reached the end, our node should be last
        parent.orderBefore(nodeName, null);
      }
    }

    @Override
    public boolean isOrdered() {
      return true;
    }
  }
}