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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule;
import com.day.cq.search.Predicate;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.eval.JcrPropertyPredicateEvaluator;
import com.day.cq.search.eval.PathPredicateEvaluator;
import com.day.cq.search.eval.TypePredicateEvaluator;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
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
public class ComponentRewriteRuleServiceImpl implements ComponentRewriteRuleService {

  private static final Logger logger = LoggerFactory.getLogger(ComponentRewriteRuleServiceImpl.class);
  /**
   * Keeps track of OSGi services implementing component rewrite rules
   */
  private final RankedServices<ComponentRewriteRule> rules = new RankedServices<>(Order.ASCENDING);
  private Config config;

  @Reference
  private QueryBuilder queryBuilder;

  @SuppressWarnings("unused")
  public void bindRule(ComponentRewriteRule rule, Map<String, Object> properties) {
    rules.bind(rule, properties);
  }

  @SuppressWarnings("unused")
  public void unbindRule(ComponentRewriteRule rule, Map<String, Object> properties) {
    rules.unbind(rule, properties);
  }

  @Override
  public void apply(@NotNull Resource resource, @NotNull Set<String> rules, boolean deep) throws RewriteException {
    ResourceResolver rr = resource.getResourceResolver();

    List<RewriteRule> rewrites = create(rr, rules);
    Node node = resource.adaptTo(Node.class);
    try {
      if (deep) {
        new ComponentTreeRewriter(rewrites).rewrite(node);
      } else {
        applyTo(rewrites, node);
      }
    } catch (RepositoryException e) {
      logger.error("Error occurred while trying to perform a rewrite operation.", e);
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
  }

  private void applyTo(List<RewriteRule> rewrites, Node node) throws RepositoryException, RewriteException {
    String nodeName = node.getName();
    String prevName = null;
    Node parent = node.getParent();
    boolean isOrdered = parent.getPrimaryNodeType().hasOrderableChildNodes();
    if (isOrdered) {
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
    }
    for (RewriteRule rule : rewrites) {
      if (rule.matches(node)) {
        rule.applyTo(node, new HashSet<>());
        break;
      }
    }
    // Previous not set - we should be first in the order - if previous and first item in list, we're the only child.
    if (isOrdered && prevName == null) {
      String nextName = parent.getNodes().nextNode().getName();
      if (!nextName.equals(nodeName)) {
        parent.orderBefore(nodeName, nextName);
      }
    } else {
      NodeIterator siblings = parent.getNodes();
      while (!siblings.nextNode().getName().equals(prevName)) {
        // There has to be a better way to skip through a parent's children nodes.
      }
      parent.orderBefore(nodeName, siblings.nextNode().getName());
    }
  }

  @Override
  @NotNull
  public Set<String> findResources(Resource resource) {
    Set<String> paths = new HashSet<>();
    paths.addAll(findByNodeRules(resource));
    paths.addAll(findByService(resource));
    return paths;
  }

  @Override
  public @NotNull Set<RewriteRule> listRules(ResourceResolver rr, String... slingResourceTypes) {
    Set<RewriteRule> rules = new HashSet<>();
    rules.addAll(listRulesByNode(rr, slingResourceTypes));
    rules.addAll(listRulesByService(slingResourceTypes));
    return rules;
  }

  private Set<String> findByNodeRules(Resource resource) {

    ResourceResolver rr = resource.getResourceResolver();
    Set<String> types = getSlingResourceTypes(rr);
    if (types.isEmpty()) {
      return Collections.emptySet();
    }
    PredicateGroup predicates = new PredicateGroup();

    Predicate predicate = new Predicate(TypePredicateEvaluator.TYPE);
    predicate.set(TypePredicateEvaluator.TYPE, JcrConstants.NT_UNSTRUCTURED);
    predicates.add(predicate);

    predicate = new Predicate(PathPredicateEvaluator.PATH);
    predicate.set(PathPredicateEvaluator.PATH, resource.getPath());
    predicates.add(predicate);

    predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
    predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, ResourceResolver.PROPERTY_RESOURCE_TYPE);
    int i = 0;
    for (String type : types) {
      predicate.set(String.format("%d_%s", i++, JcrPropertyPredicateEvaluator.VALUE), type);
    }
    predicate.set(JcrPropertyPredicateEvaluator.OPERATION, JcrPropertyPredicateEvaluator.OP_EQUALS);
    predicate.set(JcrPropertyPredicateEvaluator.AND, "false");
    predicates.add(predicate);

    return getHitPaths(predicates, rr);
  }

  private Set<String> findByService(Resource resource) {
    Set<String> paths = new HashSet<>();
    for (ComponentRewriteRule rule : rules.getList()) {
      paths.addAll(rule.findMatches(resource));
    }
    return paths;
  }

  private Set<RewriteRule> listRulesByNode(ResourceResolver rr, String... slingResourceTypes) {
    if (slingResourceTypes == null || slingResourceTypes.length == 0) {
      return Collections.emptySet();
    }

    PredicateGroup predicates = new PredicateGroup();
    for (String path : config.search_paths()) {
      PredicateGroup pg = new PredicateGroup();

      Predicate predicate = new Predicate(TypePredicateEvaluator.TYPE);
      predicate.set(TypePredicateEvaluator.TYPE, JcrConstants.NT_UNSTRUCTURED);
      predicates.add(predicate);

      predicate = new Predicate(PathPredicateEvaluator.PATH);
      predicate.set(PathPredicateEvaluator.PATH, path);
      pg.add(predicate);

      predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
      predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, ResourceResolver.PROPERTY_RESOURCE_TYPE);
      int i = 0;
      for (String type : slingResourceTypes) {
        predicate.set(String.format("%d_%s", i++, JcrPropertyPredicateEvaluator.VALUE), type);
      }
      predicate.set(JcrPropertyPredicateEvaluator.OPERATION, JcrPropertyPredicateEvaluator.OP_EQUALS);
      predicate.set(JcrPropertyPredicateEvaluator.AND, "false");
      pg.add(predicate);
      predicates.add(pg);
    }

    Set<String> paths = getHitPaths(predicates, rr);
    return paths.stream().map(p -> {
      Resource r = rr.getResource(p);
      if (r == null || r.getParent() == null || !StringUtils.equals(NodeBasedRewriteRule.NN_PATTERNS, r.getParent().getName())) {
        return null;
      }
      r = r.getParent().getParent();
      Node rule = r == null ? null : r.adaptTo(Node.class);
      try {
        return new NodeBasedRewriteRule(rule);
      } catch (RepositoryException e) {
        logger.error("Unable to create Rewrite Rule from Node ({})", r.getPath());
        return null;
      }
    }).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private Set<RewriteRule> listRulesByService(String... slingResourceTypes) {
    Set<RewriteRule> services = new HashSet<>();
    for (ComponentRewriteRule rule : rules.getList()) {
      if (rule.hasPattern(slingResourceTypes)) {
        services.add(rule);
      }
    }
    return services;
  }

  private List<RewriteRule> create(ResourceResolver rr, Set<String> rules) {
    List<String> searchPaths = Arrays.asList(config.search_paths());

    return rules.stream().map(id -> {
          RewriteRule rule = null;
          if (PathUtils.isAbsolute(id)) {
            Resource r = rr.getResource(id);
            if (r != null && searchPaths.contains(PathUtils.getParentPath(r.getPath()))) {
              try {
                rule = new NodeBasedRewriteRule(r.adaptTo(Node.class));
              } catch (RepositoryException e) {
                logger.error(String.format("Unable to create RewriteRule for path: {}", id), e);
              }
            }
          } else { // Assume non-absolute path is a PID
            rule = StreamSupport.stream(this.rules.spliterator(), false).filter(r -> StringUtils.equals(id, r.getId())).findFirst().orElse(null);
          }
          return rule;
        })
        .filter(Objects::nonNull)
        .sorted(new RewriteRule.Comparator())
        .collect(Collectors.toList());

  }

  private Set<String> getSlingResourceTypes(ResourceResolver rr) {
    Set<String> types = new HashSet<>();

    PredicateGroup predicates = new PredicateGroup();
    Predicate predicate = new Predicate(TypePredicateEvaluator.TYPE);
    predicate.set(TypePredicateEvaluator.TYPE, JcrConstants.NT_UNSTRUCTURED);
    predicates.add(predicate);

    predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
    predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, ResourceResolver.PROPERTY_RESOURCE_TYPE);
    predicate.set(JcrPropertyPredicateEvaluator.OPERATION, JcrPropertyPredicateEvaluator.OP_EXISTS);
    predicates.add(predicate);

    PredicateGroup pg = new PredicateGroup("paths");
    pg.setAllRequired(false);
    for (String path : config.search_paths()) {
      predicate = new Predicate(PathPredicateEvaluator.PATH);
      predicate.set(PathPredicateEvaluator.PATH, path);
      pg.add(predicate);
    }
    predicates.add(pg);

    Set<String> hits = getHitPaths(predicates, rr);
    for (String path : hits) {
      Resource r = rr.getResource(path);
      if (r != null && r.getParent() != null && StringUtils.equals(NodeBasedRewriteRule.NN_PATTERNS, r.getParent().getName())) {
        ValueMap vm = r.getValueMap();
        types.add(vm.get(ResourceResolver.PROPERTY_RESOURCE_TYPE, String.class));
      }
    }
    return types;
  }

  private Set<String> getHitPaths(PredicateGroup predicates, ResourceResolver rr) {
    Set<String> paths = new HashSet<>();
    Query query = queryBuilder.createQuery(predicates, rr.adaptTo(Session.class));
    SearchResult results = query.getResult();
    // QueryBuilder has a leaking ResourceResolver, so the following workaround is required.
    ResourceResolver qrr = null;
    try {
      for (final Hit hit : results.getHits()) {
        if (qrr == null) {
          qrr = hit.getResource().getResourceResolver();
        }
        paths.add(hit.getPath());
      }
    } catch (RepositoryException e) {
      logger.error("Encountered an error when trying to gather all of the resources that match the rules.", e);
    } finally {
      if (qrr != null) {
        // Always close the leaking QueryBuilder resourceResolver.
        qrr.close();
      }
    }
    return paths;
  }

  @Activate
  @Modified
  protected void activate(Config config) {
    this.config = config;
  }

  @ObjectClassDefinition(
      name = "Component Rewrite Rule Service",
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
