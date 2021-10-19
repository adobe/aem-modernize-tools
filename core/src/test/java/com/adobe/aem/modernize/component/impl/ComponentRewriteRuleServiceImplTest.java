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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.MockHit;
import com.adobe.aem.modernize.MockRule;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.impl.TreeRewriter;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ComponentRewriteRuleServiceImplTest {

  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  private final String[] RULE_PATHS = new String[] { "/apps/aem-modernize/component/rules", "/apps/customer/component/rules" };
  private ComponentRewriteRuleService componentRewriteRuleService = new ComponentRewriteRuleServiceImpl();

  @Mocked
  private ComponentRewriteRule matchedRewriteRule;

  @Mocked
  private ComponentRewriteRule notMatchedRewriteRule;

  @Mocked
  private QueryBuilder queryBuilder;

  @BeforeEach
  public void beforeEach() {
    for (String path : RULE_PATHS) {
      context.load().json("/component/test-rules.json", path);
    }
    context.load().json("/component/test-rules.json", "/apps/not-registered/component/rules");
    context.load().json("/component/shallow-content.json", "/content/test/shallow");
    context.load().json("/component/deep-content.json", "/content/test/deep");
    context.load().json("/component/all-content.json", "/content/test/all");

    Map<String, Object> props = new HashMap<>();
    props.put("search.paths", RULE_PATHS);

    context.registerService(ComponentRewriteRule.class, notMatchedRewriteRule);
    context.registerService(ComponentRewriteRule.class, matchedRewriteRule);
    context.registerService(QueryBuilder.class, queryBuilder);
    context.registerInjectActivateService(componentRewriteRuleService, props);
  }

  @Test
  public void testShallowApply() throws Exception {

    Set<String> rules = new HashSet<>(Arrays.asList(
        "/apps/aem-modernize/component/rules/simple",
        "/apps/aem-modernize/component/rules/not-found",
        "/apps/not-registered/component/rules/simple",
        "com.adobe.aem.modernize.component.ComponentRewriteRuleFound",
        "/apps/aem-modernize/component/rules/rewriteOptional"
    ));

    final int[] callCounts = { 0, 0 };
    new MockUp<NodeBasedRewriteRule>() {

      private List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(Node root) throws RepositoryException {
        return !StringUtils.equals(paths.get(callCounts[0]++), "/apps/aem-modernize/component/rules/rewriteOptional");
      }

      @Mock
      public Node applyTo(Node root, Set<Node> finalNodes) throws RepositoryException {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };

    new Expectations() {{
      matchedRewriteRule.getId();
      result = "com.adobe.aem.modernize.component.ComponentRewriteRuleFound";
    }};
    Resource resource = context.resourceResolver().getResource("/content/test/shallow/simple");
    componentRewriteRuleService.apply(resource, rules, false);

    assertEquals(2, callCounts[0], "Correct number of matched called.");
    assertEquals(1, callCounts[1], "Correct number of apply called.");
  }

  @Test
  public void testDeepApply() throws Exception {
    final boolean[] called = { false, false };
    Set<String> rules = new HashSet<>(Arrays.asList(
        "/apps/aem-modernize/component/rules/simple",
        "/apps/aem-modernize/component/rules/not-found",
        "/apps/not-registered/component/rules/simple",
        "com.adobe.aem.modernize.component.ComponentRewriteRuleFound",
        "/apps/aem-modernize/component/rules/rewriteOptional"
    ));
    new MockUp<TreeRewriter>() {
      @Mock
      public void $init(List<RewriteRule> rules) {
        called[0] = true;
        assertNotNull(rules);
      }

      @Mock
      public Node rewrite(Node root) {
        called[1] = true;
        assertNotNull(root);
        return root;
      }
    };
    new Expectations() {{
      matchedRewriteRule.getId();
      result = "com.adobe.aem.modernize.component.ComponentRewriteRuleFound";
    }};
    Resource resource = context.resourceResolver().getResource("/content/test/deep/parent");
    componentRewriteRuleService.apply(resource, rules, true);
    assertTrue(called[0], "TreeRewriter instantiated");
    assertTrue(called[1], "TreeRewriter called");
  }

  @Test
  public <R extends ResourceResolver> void testFindRulesQueryFails(
      @Mocked Query query,
      @Mocked SearchResult searchResult
  ) throws Exception {
    new Expectations() {{
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 1;
      query.getResult();
      result = searchResult;
      times = 1;
      searchResult.getHits();
      result = new RepositoryException("Error");

      matchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/serviceTest");
      notMatchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = componentRewriteRuleService.findResources(root);
    assertTrue(paths.contains("/content/test/all/serviceTest"));
    paths.remove("/content/test/all/serviceTest");
    assertTrue(paths.isEmpty(), "Paths content");
  }

  @Test
  public <R extends ResourceResolver> void testFindPagesQueryFails(
      @Mocked Query query,
      @Mocked SearchResult searchResult
  ) throws Exception {

    final boolean[] closeCalled = { false };

    new MockUp<R>() {
      @Mock
      public void close() {
        closeCalled[0] = true;
      }
    };
    new Expectations() {{
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 2;
      query.getResult();
      result = searchResult;
      times = 2;
      searchResult.getHits();
      result = buildRuleHits();
      result = new RepositoryException("Error");

      matchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/serviceTest");
      notMatchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = componentRewriteRuleService.findResources(root);
    assertTrue(paths.contains("/content/test/all/serviceTest"));
    paths.remove("/content/test/all/serviceTest");
    assertTrue(paths.isEmpty(), "Paths content");
    assertTrue(closeCalled[0], "Query RR was closed");
  }

  @Test
  public <R extends ResourceResolver> void testFind(
      @Mocked Query query,
      @Mocked SearchResult searchResult
  ) throws Exception {

    final int[] closeCalled = { 0 };

    new MockUp<R>() {
      @Mock
      public void close() {
        closeCalled[0]++;
      }
    };

    new Expectations() {{
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 2;
      query.getResult();
      result = searchResult;
      times = 2;
      searchResult.getHits();
      returns(buildRuleHits(), buildComponentHits());

      matchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/serviceTest");
      notMatchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = componentRewriteRuleService.findResources(root);

    assertTrue(paths.contains("/content/test/all/simple"), "Simple rule");
    paths.remove("/content/test/all/simple");
    assertTrue(paths.contains("/content/test/all/copyChildren"), "Copy children rule");
    paths.remove("/content/test/all/copyChildren");
    assertTrue(paths.contains("/content/test/all/copyChildrenOrder"), "Copy children order rule");
    paths.remove("/content/test/all/copyChildrenOrder");
    assertTrue(paths.contains("/content/test/all/mapProperties"), "Map properties rule");
    paths.remove("/content/test/all/mapProperties");
    assertTrue(paths.contains("/content/test/all/rewriteOptional"), "Rewrite Optional rule");
    paths.remove("/content/test/all/rewriteOptional");
    assertTrue(paths.contains("/content/test/all/rewriteRanking"), "Ranking rule");
    paths.remove("/content/test/all/rewriteRanking");
    assertTrue(paths.contains("/content/test/all/rewriteMapChildren"), "Rewrite map children rule");
    paths.remove("/content/test/all/rewriteMapChildren");
    assertTrue(paths.contains("/content/test/all/rewriteFinal"), "Rewrite final rule");
    paths.remove("/content/test/all/rewriteFinal");
    assertTrue(paths.contains("/content/test/all/rewriteFinalOnReplacement"), "Rewrite final on replacement node rule.");
    paths.remove("/content/test/all/rewriteFinalOnReplacement");
    assertTrue(paths.contains("/content/test/all/rewriteProperties"), "Rewrite properties rule");
    paths.remove("/content/test/all/rewriteProperties");
    assertTrue(paths.contains("/content/test/all/serviceTest"), "Service rule");
    paths.remove("/content/test/all/serviceTest");
    assertTrue(paths.isEmpty(), "Rule count");

    assertEquals(2, closeCalled[0], "Query RR was closed");
  }

  @Test
  public <R extends ResourceResolver> void testListRules(
      @Mocked Query query,
      @Mocked SearchResult searchResult
  ) throws Exception {
    final int[] closeCalled = { 0 };

    new MockUp<R>() {
      @Mock
      public void close() {
        closeCalled[0]++;
      }
    };

    final String[] foundRuleList = new String[] {
        "/apps/aem-modernize/component/rules/simple/patterns/simple",
        "/apps/aem-modernize/component/rules/copyChildren/patterns/copyChildren"
    };

    final String[] resourceTypes = new String[] {
        "aem-modernize/components/simple",
        "aem-modernize/components/copyChildren",
        "aem-modenrize/components/service"
    };

    new Expectations() {{
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      query.getResult();
      result = searchResult;
      searchResult.getHits();
      result = listRulesAsHits(foundRuleList);
      matchedRewriteRule.hasPattern(resourceTypes);
      result = true;
      notMatchedRewriteRule.hasPattern(resourceTypes);
      result = false;
    }};
    Set<RewriteRule> rules = componentRewriteRuleService.listRules(context.resourceResolver(), resourceTypes);

    assertEquals(3, rules.size(), "Rule path count");
    assertTrue(rules.contains(new MockRule("/apps/aem-modernize/component/rules/simple")), "simple rule returned");
    assertTrue(rules.contains(new MockRule("/apps/aem-modernize/component/rules/copyChildren")), "copyChildren rule returned");
    assertTrue(rules.contains(matchedRewriteRule), "Service rule returned");
    assertEquals(1, closeCalled[0], "Query RR was closed");

  }

  private List<Hit> buildRuleHits() throws Exception {
    List<Hit> hits = new ArrayList<>();
    ResourceResolver rr = context.resourceResolver();
    for (String path : RULE_PATHS) {
      Resource ruleParent = rr.getResource(path);
      for (Node node : new TreeTraverser(ruleParent.adaptTo(Node.class))) {
        if (node.hasProperty(ResourceResolver.PROPERTY_RESOURCE_TYPE)) {
          hits.add(new MockHit(rr.getResource(node.getPath())));
        }
      }
    }
    return hits;
  }

  private List<Hit> buildComponentHits() {
    List<Hit> hits = new ArrayList<>();
    Resource ruleParent = context.resourceResolver().getResource("/content/test/all");
    for (Resource child : ruleParent.getChildren()) {
      hits.add(new MockHit(child));
    }
    return hits;
  }

  private List<Hit> listRulesAsHits(String... paths) {
    List<Hit> hits = new ArrayList<>();
    ResourceResolver rr = context.resourceResolver();
    for (String path : paths) {
      hits.add(new MockHit(rr.getResource(path)));
    }
    return hits;
  }
}
