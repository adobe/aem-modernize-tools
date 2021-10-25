package com.adobe.aem.modernize.rule.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
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

  public @NotNull Set<String> find(@NotNull Resource resource) {
    Set<String> mappings = new HashSet<>();
    mappings.addAll(findByNodeRules(resource));
    mappings.addAll(findByService(resource));
    return mappings;
  }

  public @NotNull Set<RewriteRule> listRules(ResourceResolver rr, String... slingResourceTypes) {
    Set<RewriteRule> rules = new HashSet<>();
    rules.addAll(listRulesByNode(rr, slingResourceTypes));
    rules.addAll(listRulesByService(slingResourceTypes));
    return rules;
  }

  @NotNull
  protected abstract String[] getSearchPaths();

  @Nullable
  protected RewriteRule getNodeRule(@NotNull Node node) {
    try {
      return new NodeBasedRewriteRule(node);
    } catch (RepositoryException e) {
      logger.error("Unable to create  PolicyImportRule", e);
    }
    return null;
  }

  @NotNull
  protected abstract List<S> getServiceRules();


  protected List<RewriteRule> create(ResourceResolver rr, Set<String> rules) {
    List<String> searchPaths = Arrays.asList(getSearchPaths());

    return rules.stream().map(id -> {
          RewriteRule rule = null;
          if (PathUtils.isAbsolute(id)) {
            Resource r = rr.getResource(id);
            if (r != null && searchPaths.contains(PathUtils.getParentPath(r.getPath()))) {
              rule = getNodeRule(r.adaptTo(Node.class));
            }
          } else { // Assume non-absolute path is a PID
            rule = StreamSupport.stream(getServiceRules().spliterator(), false).filter(r -> StringUtils.equals(id, r.getId())).findFirst().orElse(null);
          }
          return rule;
        })
        .filter(Objects::nonNull)
        .sorted(new RewriteRule.Comparator())
        .collect(Collectors.toList());

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

    Predicate predicate = new Predicate(TypePredicateEvaluator.TYPE);
    predicate.set(TypePredicateEvaluator.TYPE, JcrConstants.NT_UNSTRUCTURED);
    predicates.add(predicate);

    predicate = new Predicate(PathPredicateEvaluator.PATH);
    predicate.set(PathPredicateEvaluator.PATH, resource.getPath());
    predicates.add(predicate);

    predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
    predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
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
    for (ServiceBasedRewriteRule rule : getServiceRules()) {
      paths.addAll(rule.findMatches(resource));
    }
    return paths;
  }

  private Set<RewriteRule> listRulesByNode(ResourceResolver rr, String... slingResourceTypes) {
    if (slingResourceTypes == null || slingResourceTypes.length == 0) {
      return Collections.emptySet();
    }

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


  protected Set<RewriteRule> listRulesByService(String... slingResourceTypes) {
    Set<RewriteRule> services = new HashSet<>();
    for (ServiceBasedRewriteRule rule : getServiceRules()) {
      if (rule.hasPattern(slingResourceTypes)) {
        services.add(rule);
      }
    }
    return services;
  }


  private Set<String> getHitPaths(PredicateGroup predicates, ResourceResolver rr) {
    Set<String> paths = new HashSet<>();
    Query query = rr.adaptTo(QueryBuilder.class).createQuery(predicates, rr.adaptTo(Session.class));
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
