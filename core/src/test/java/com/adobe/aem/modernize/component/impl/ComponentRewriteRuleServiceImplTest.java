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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule;
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
  private static final String FOUND_SERVICE_ID = "com.adobe.aem.modernize.component.ComponentRewriteRuleFound";
  private static final String NOT_FOUND_SERVICE_ID = "com.adobe.aem.modernize.component.ComponentRewriteRuleNotFound";
  private final ComponentRewriteRuleService componentRewriteRuleService = new ComponentRewriteRuleServiceImpl();

  @Mocked
  private ComponentRewriteRule matchedRewriteRule;

  @Mocked
  private ComponentRewriteRule notMatchedRewriteRule;


  @BeforeEach
  public void beforeEach() {
    new Expectations() {{
      matchedRewriteRule.getId();
      result = FOUND_SERVICE_ID;
      notMatchedRewriteRule.getId();
      result = NOT_FOUND_SERVICE_ID;
    }};
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
    final boolean[] returns = { false, true};
    new MockUp<NodeBasedRewriteRule>() {

      private final List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(@NotNull Node root) throws RepositoryException {
        return returns[callCounts[0]++];
      }

      @Mock
      public Node applyTo(@NotNull Node root, @NotNull Set<Node> finalNodes) throws RepositoryException {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };

    Resource resource = context.resourceResolver().getResource("/content/test/shallow/simple");
    componentRewriteRuleService.apply(resource, rules);

    assertEquals(2, callCounts[0], "Correct number of matched called.");
    assertEquals(1, callCounts[1], "Correct number of apply called.");
  }

  @Test
  public void testDeepApply() throws Exception {
    final boolean[] called = { false };
    Set<String> rules = new HashSet<>(Arrays.asList(
        "/apps/aem-modernize/component/rules/simple",
        "/apps/aem-modernize/component/rules/not-found",
        "/apps/not-registered/component/rules/simple",
        "com.adobe.aem.modernize.component.ComponentRewriteRuleFound",
        "/apps/aem-modernize/component/rules/rewriteOptional"
    ));
    new MockUp<ComponentTreeRewriter>() {

      @Mock
      public Node rewrite(Node root, List<RewriteRule> rules) {
        called[0] = true;
        assertNotNull(root);
        assertNotNull(rules);
        return root;
      }
    };
    
    Resource resource = context.resourceResolver().getResource("/content/test/deep/parent");
    componentRewriteRuleService.apply(resource, rules, true);
    assertTrue(called[0], "TreeRewriteProcessor called");
  }

}
