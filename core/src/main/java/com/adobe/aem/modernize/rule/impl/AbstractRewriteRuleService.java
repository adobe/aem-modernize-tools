package com.adobe.aem.modernize.rule.impl;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.adobe.aem.modernize.rule.ServiceBasedRewriteRule;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRewriteRuleService<S extends ServiceBasedRewriteRule> implements RewriteRuleService {

  private static final Logger logger = LoggerFactory.getLogger(AbstractRewriteRuleService.class);

  protected final RankedServices<S> rules = new RankedServices<>(Order.ASCENDING);
  protected final Map<String, S> ruleMap = new HashMap<>();

  @NotNull
  protected abstract List<String> getSearchPaths();

  @NotNull
  protected List<S> getServiceRules() {
    return Collections.unmodifiableList(rules.getList());
  }

  @NotNull
  @Override
  public Set<String> listRules(@NotNull Resource resource) {
    ResourceResolver rr = resource.getResourceResolver();
    List<String> types = Collections.singletonList(resource.getResourceType());
    logger.debug("Finding rules for resource types: [{}]", types);
    final Set<String> rules = new HashSet<>(searchRules(rr, types));
    for (ServiceBasedRewriteRule s : getServiceRules()) {
      if (s.hasPattern(types.toArray(new String[]{}))) {
        logger.debug("Found service based rule: [{}]", s.getId());
        rules.add(s.getId());
      }
    }
    return rules;
  }
  
  @Override
  @Nullable
  public RewriteRule getRule(@NotNull ResourceResolver resourceResolver, @NotNull String id) {
    if (PathUtils.isAbsolute(id)) {
      Resource resource = resourceResolver.getResource(id);
      if (resource == null || resource.adaptTo(Node.class) == null) {
        return null;
      }
      return getNodeRule(resource.adaptTo(Node.class));
    }
    return getServiceRuleMap().get(id);
  }

  @NotNull
  protected Map<String, S> getServiceRuleMap() {
    return Collections.unmodifiableMap(ruleMap);
  }

  @NotNull
  protected List<RewriteRule> create(ResourceResolver rr, Set<String> rules) {
    List<String> searchPaths = getSearchPaths();

    return rules.stream().map(id -> {
          RewriteRule rule = null;
          if (PathUtils.isAbsolute(id)) {
            Resource r = rr.getResource(id);
            if (r != null && searchPaths.contains(PathUtils.getParentPath(r.getPath()))) {
              rule = getNodeRule(r.adaptTo(Node.class));
            }
          } else { // Assume non-absolute path is a PID
            rule = getServiceRuleMap().get(id);
          }
          return rule;
        })
        .filter(Objects::nonNull)
        .sorted(new RewriteRule.Comparator())
        .collect(Collectors.toList());
  }
  
  @Nullable
  protected RewriteRule getNodeRule(@NotNull Node node) {
    try {
      return new NodeBasedRewriteRule(node);
    } catch (RepositoryException e) {
      logger.error("Unable to create NodeBasedRewriteRule", e);
    }
    return null;
  }

  private Set<String> searchRules(ResourceResolver rr, List<String> resourceTypes) {

    PredicateGroup predicates = new PredicateGroup();
    for (String path : getSearchPaths()) {
      PredicateGroup pg = new PredicateGroup();

      Predicate predicate = new Predicate(TypePredicateEvaluator.TYPE);
      predicate.set(TypePredicateEvaluator.TYPE, JcrConstants.NT_UNSTRUCTURED);
      predicates.add(predicate);

      predicate = new Predicate(PathPredicateEvaluator.PATH);
      predicate.set(PathPredicateEvaluator.PATH, path);
      pg.add(predicate);

      predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
      predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
      int i = 0;
      for (String type : resourceTypes) {
        predicate.set(String.format("%d_%s", i++, JcrPropertyPredicateEvaluator.VALUE), type);
      }
      predicate.set(JcrPropertyPredicateEvaluator.OPERATION, JcrPropertyPredicateEvaluator.OP_EQUALS);
      predicate.set(JcrPropertyPredicateEvaluator.AND, "false");
      pg.add(predicate);
      predicates.add(pg);
    }

    Set<String> paths = getHitPaths(predicates, rr);
    return getRulePathsFromHits(rr, paths);
  }

  private Set<String> getHitPaths(PredicateGroup predicates, ResourceResolver rr) {
    Set<String> paths = new HashSet<>();
    long limit = 50;
    long offset = 0;
    boolean more = true;

    QueryBuilder qb = rr.adaptTo(QueryBuilder.class);

    ResourceResolver qrr = null;
    try {
      while (more) {
        predicates.set(Predicate.PARAM_GUESS_TOTAL, "true");
        Query query = qb.createQuery(predicates, rr.adaptTo(Session.class));
        query.setHitsPerPage(limit);
        query.setStart(offset);
        SearchResult results = query.getResult();
        for (final Hit hit : results.getHits()) {
          if (qrr == null) {
            qrr = hit.getResource().getResourceResolver();
          }
          paths.add(hit.getPath());
        }
        more = results.hasMore();
        offset += limit;
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
  
  private Set<String> getRulePathsFromHits(ResourceResolver rr, Set<String> paths) {
    return paths.stream().map(p -> {
      Resource r = rr.getResource(p);
      if (r == null || r.getParent() == null) {
        return null;
      }
      Resource parent = r.getParent();
      if (parent != null && StringUtils.equals(NodeBasedRewriteRule.NN_REPLACEMENT, parent.getName())) {
        return null;
      }
      if (parent != null && StringUtils.equals(NodeBasedRewriteRule.NN_PATTERNS, parent.getName())) {
        parent = parent.getParent(); // At Patterns node.
      } 
      if (parent != null && StringUtils.equals(NodeBasedRewriteRule.NN_AGGREGATE, parent.getName())) {
        parent = parent.getParent(); // At Aggregate node if valid
      }
      if (parent == null) {
        return null;
      }
      String path = parent.getPath();
      logger.debug("Found node based rule: [{}]", path);
      return path;
    }).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  // Deprecated Code
  
  @NotNull
  @Deprecated(since = "2.1.0")
  public Set<RewriteRule> listRules(ResourceResolver rr, String... slingResourceTypes) {
    Set<RewriteRule> rules = new HashSet<>();
    rules.addAll(listRulesByNode(rr, slingResourceTypes));
    rules.addAll(listRulesByService(slingResourceTypes));
    return rules;
  }
  
  @NotNull
  @Deprecated(since = "2.1.0")
  public Set<String> find(@NotNull Resource resource) {
    Set<String> mappings = new HashSet<>();
    mappings.addAll(findByNodeRules(resource));
    mappings.addAll(findByService(resource));
    return mappings;
  }
  
  private Set<String> findByNodeRules(Resource resource) {
    ResourceResolver rr = resource.getResourceResolver();
    Set<String> types = new HashSet<>();
    for (String path : getSearchPaths()) {
      Resource root = rr.getResource(path);
      if (root != null) {
        types.addAll(getSlingResourceTypes(root));
      }
    }

    if (types.isEmpty()) {
      return Collections.emptySet();
    }
    PredicateGroup predicates = new PredicateGroup();

    PredicateGroup nodeTypes = new PredicateGroup();
    nodeTypes.setAllRequired(false);
    Predicate predicate = new Predicate(TypePredicateEvaluator.TYPE);
    predicate.set(TypePredicateEvaluator.TYPE, JcrConstants.NT_UNSTRUCTURED);
    nodeTypes.add(predicate);
    
    predicate = new Predicate(TypePredicateEvaluator.TYPE);
    predicate.set(TypePredicateEvaluator.TYPE, JcrConstants.NT_FROZENNODE);
    nodeTypes.add(predicate);
    
    predicates.add(nodeTypes);

    predicate = new Predicate(PathPredicateEvaluator.PATH);
    predicate.set(PathPredicateEvaluator.PATH, resource.getPath());
    predicates.add(predicate);

    predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
    predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
    int i = 0;
    for (String type : types) {
      predicate.set(String.format("%d_%s", i++, JcrPropertyPredicateEvaluator.VALUE), String.format("%%%s%%", type));
    }
    predicate.set(JcrPropertyPredicateEvaluator.OPERATION, JcrPropertyPredicateEvaluator.OP_LIKE);
    predicate.set(JcrPropertyPredicateEvaluator.AND, "false");
    predicates.add(predicate);

    return getHitPaths(predicates, rr);

  }

  private Set<String> findByService(Resource resource) {
    Set<String> paths = new HashSet<>();
    for (ServiceBasedRewriteRule rule : getServiceRules()) {
      paths.addAll(rule.findMatches(resource));
    }
    return paths;
  }

  private Set<RewriteRule> listRulesByNode(ResourceResolver rr, String... slingResourceTypes) {
    if (slingResourceTypes == null || slingResourceTypes.length == 0) {
      return Collections.emptySet();
    }
    return searchRules(rr, Arrays.asList(slingResourceTypes)).stream().map(s -> {
      try {
        Node node = rr.getResource(s).adaptTo(Node.class);
        return new NodeBasedRewriteRule(node);
      } catch (RepositoryException e) {
        logger.error("Unable to create Rewrite Rule from Node ({})", s);
        return null;
      }
    }).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  protected Set<RewriteRule> listRulesByService(String... slingResourceTypes) {
    Set<RewriteRule> services = new HashSet<>();
    for (ServiceBasedRewriteRule rule : getServiceRules()) {
      if (rule.hasPattern(slingResourceTypes)) {
        services.add(rule);
      }
    }
    return services;
  }

  private static Function<Resource, Set<String>> getPatternResourceTypes() {
    return (resource) -> {
      Set<String> types = new HashSet<>();
      Resource patterns = resource.getChild(NodeBasedRewriteRule.NN_PATTERNS);
      if (patterns != null) {
        patterns.getChildren().forEach(p -> types.add(p.getResourceType()));
      }
      return types;
    };
  }

  /**
   * Returns a list of the {@code sling:resourceType}s patterns for the rules rooted at the specified resource location.
   *
   * @param ruleTreeRoot the root of the rule tree
   * @return set of {@code sling:resourceType}s or an empty list.
   */
  @NotNull
  @Deprecated(since = "2.1.0")
  protected static Set<String> getSlingResourceTypes(@NotNull Resource ruleTreeRoot) {
    final Set<String> types = new HashSet<>();
    ruleTreeRoot.getChildren().forEach(r -> {
      if (r.getChild(NodeBasedRewriteRule.NN_AGGREGATE) != null) {
        Resource aggregate = r.getChild(NodeBasedRewriteRule.NN_AGGREGATE);
        types.addAll(getPatternResourceTypes().apply(aggregate));
      } else {
        types.addAll(getPatternResourceTypes().apply(r));
      }
    });
    return types;
  }
}
