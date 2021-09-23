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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.impl.TreeRewriter;
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
  private ComponentRewriteRuleService componentRewriteRuleService = new ComponentRewriteRuleServiceImpl();

  @Mocked
  private ComponentRewriteRule matchedRewriteRule;

  @Mocked
  private ComponentRewriteRule notMatchedRewriteRule;

  @BeforeEach
  public void beforeEach() {
    for (String path : RULE_PATHS) {
      context.load().json("/component/test-rules.json", path);
    }
    context.load().json("/component/test-rules.json", "/apps/not-registered/component/rules");
    context.load().json("/component/shallow-content.json", "/content/test/shallow");
    context.load().json("/component/deep-content.json", "/content/test/deep");

    Map<String, Object> props = new HashMap<>();
    props.put("search.paths", RULE_PATHS);

    context.registerService(ComponentRewriteRule.class, notMatchedRewriteRule);
    context.registerService(ComponentRewriteRule.class, matchedRewriteRule);
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

//  @Test
//  public void testGetRules() throws Exception {
//    List<String> expectedRulePaths = new ArrayList<>();
//
//    // expected ordering based on applied ranking is also represented here
//    expectedRulePaths.addAll(Arrays.asList(
//        RULES_PATH + "/rewriteRanking",
//        RULES_PATH + "/simple",
//        RULES_PATH + "/copyChildren",
//        RULES_PATH + "/copyChildrenOrder",
//        RULES_PATH + "/mapProperties",
//        RULES_PATH + "/rewriteOptional",
//        RULES_PATH + "/rewriteMapChildren",
//        RULES_PATH + "/rewriteFinal",
//        RULES_PATH + "/rewriteFinalOnReplacement",
//        RULES_PATH + "/rewriteProperties",
//        RULES_PATH + "/nested1/rule1",
//        RULES_PATH + "/nested1/rule2",
//        RULES_PATH + "/nested2/rule1"));
//
//    List<ComponentRewriteRule> rules = componentRewriteRuleService.getRules(context.resourceResolver());
//
//    assertEquals(expectedRulePaths.size(), rules.size());
//
//    // asserts:
//    // - rules considered at root and first level folders
//    // - rules ordered based on ranking
//    int index = 0;
//    for (ComponentRewriteRule rule : rules) {
//      String path = expectedRulePaths.get(index);
//      assertTrue(rule.toString().contains("path=" + path + ","));
//      index++;
//    }
//  }

//  @Test
//  public void testGetSlingResourceTypes() throws Exception {
//    Set<String> resourceTypes = componentRewriteRuleService.getSlingResourceTypes(context.resourceResolver());
//
//    assertTrue(resourceTypes.contains("aem-modernize/components/simple"));
//    resourceTypes.remove("aem-modernize/components/simple");
//    assertTrue(resourceTypes.contains("aem-modernize/components/mapProperties"));
//    resourceTypes.remove("aem-modernize/components/mapProperties");
//    assertTrue(resourceTypes.contains("aem-modernize/components/rewriteOptional"));
//    resourceTypes.remove("aem-modernize/components/rewriteOptional");
//    assertTrue(resourceTypes.contains("aem-modernize/components/rewriteRanking"));
//    resourceTypes.remove("aem-modernize/components/rewriteRanking");
//    assertTrue(resourceTypes.contains("aem-modernize/components/rewriteMapChildren"));
//    resourceTypes.remove("aem-modernize/components/rewriteMapChildren");
//    assertTrue(resourceTypes.contains("aem-modernize/components/rewriteFinal"));
//    resourceTypes.remove("aem-modernize/components/rewriteFinal");
//    assertTrue(resourceTypes.contains("aem-modernize/components/rewriteFinalOnReplacement"));
//    resourceTypes.remove("aem-modernize/components/rewriteFinalOnReplacement");
//    assertTrue(resourceTypes.contains("aem-modernize/components/rewriteProperties"));
//    resourceTypes.remove("aem-modernize/components/rewriteProperties");
//    assertTrue(resourceTypes.contains("granite/ui/components/foundation/container"));
//    resourceTypes.remove("granite/ui/components/foundation/container");
//
//    assertTrue(resourceTypes.isEmpty());
//  }
}
