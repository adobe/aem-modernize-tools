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
import java.util.Collections;
import java.util.HashMap;
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
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

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

    String[] rules = new String[] {
        "/apps/aem-modernize/component/rules/simple",
        "/apps/aem-modernize/component/rules/not-found",
        "/apps/not-registered/component/rules/simple",
        "com.adobe.aem.modernize.component.ComponentRewriteRuleFound",
        "/apps/aem-modernize/component/rules/rewriteOptional"
    };

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

    assertEquals(1, callCounts[0], "Correct number of matched called.");
    assertEquals(1, callCounts[1], "Correct number of apply called.");
  }

  @Test
  public void testDeepApply() throws Exception {
    final boolean[] called = { false, false };
    String[] rules = new String[] {
        "/apps/aem-modernize/component/rules/simple",
        "/apps/aem-modernize/component/rules/not-found",
        "/apps/not-registered/component/rules/simple",
        "com.adobe.aem.modernize.component.ComponentRewriteRuleFound",
        "/apps/aem-modernize/component/rules/rewriteOptional"
    };
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

      matchedRewriteRule.find(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/serviceTest");
      notMatchedRewriteRule.find(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = componentRewriteRuleService.find(root);
    assertTrue(paths.contains("/content/test/all/serviceTest"));
    paths.remove("/content/test/all/serviceTest");
    assertTrue(paths.isEmpty());
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

      matchedRewriteRule.find(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/serviceTest");
      notMatchedRewriteRule.find(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = componentRewriteRuleService.find(root);
    assertTrue(paths.contains("/content/test/all/serviceTest"));
    paths.remove("/content/test/all/serviceTest");
    assertTrue(paths.isEmpty());
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

      matchedRewriteRule.find(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/serviceTest");
      notMatchedRewriteRule.find(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = componentRewriteRuleService.find(root);

    assertTrue(paths.contains("/content/test/all/simple"));
    paths.remove("/content/test/all/simple");
    assertTrue(paths.contains("/content/test/all/copyChildren"));
    paths.remove("/content/test/all/copyChildren");
    assertTrue(paths.contains("/content/test/all/copyChildrenOrder"));
    paths.remove("/content/test/all/copyChildrenOrder");
    assertTrue(paths.contains("/content/test/all/mapProperties"));
    paths.remove("/content/test/all/mapProperties");
    assertTrue(paths.contains("/content/test/all/rewriteOptional"));
    paths.remove("/content/test/all/rewriteOptional");
    assertTrue(paths.contains("/content/test/all/rewriteRanking"));
    paths.remove("/content/test/all/rewriteRanking");
    assertTrue(paths.contains("/content/test/all/rewriteMapChildren"));
    paths.remove("/content/test/all/rewriteMapChildren");
    assertTrue(paths.contains("/content/test/all/rewriteFinal"));
    paths.remove("/content/test/all/rewriteFinal");
    assertTrue(paths.contains("/content/test/all/rewriteFinalOnReplacement"));
    paths.remove("/content/test/all/rewriteFinalOnReplacement");
    assertTrue(paths.contains("/content/test/all/rewriteProperties"));
    paths.remove("/content/test/all/rewriteProperties");
    assertTrue(paths.contains("/content/test/all/serviceTest"));
    paths.remove("/content/test/all/serviceTest");
    assertTrue(paths.isEmpty());

    assertEquals(2, closeCalled[0], "Query RR was closed");
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

  private static class MockHit implements Hit {

    private final Resource resource;

    private MockHit(Resource resource) {
      this.resource = resource;
    }

    @Override
    public Resource getResource() throws RepositoryException {
      return this.resource;
    }

    @Override
    public Node getNode() throws RepositoryException {
      return this.resource.adaptTo(Node.class);
    }

    @Override
    public String getPath() throws RepositoryException {
      return this.resource.getPath();
    }

    @Override
    public ValueMap getProperties() throws RepositoryException {
      return this.resource.getValueMap();
    }

    @Override
    public long getIndex() {
      throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public Map<String, String> getExcerpts() throws RepositoryException {
      throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public String getExcerpt() throws RepositoryException {
      throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public String getTitle() throws RepositoryException {
      throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public double getScore() throws RepositoryException {
      throw new UnsupportedOperationException("Unsupported");
    }
  }
}
