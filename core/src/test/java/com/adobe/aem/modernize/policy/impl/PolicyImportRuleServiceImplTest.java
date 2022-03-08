package com.adobe.aem.modernize.policy.impl;

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
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.policy.PolicyImportRule;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.policy.rule.impl.NodeBasedPolicyImportRule;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.designer.Style;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class PolicyImportRuleServiceImplTest {

  private static final String CONF_PATH = "/conf/test";
  private static final String FOUND_SERVICE_ID = "com.adobe.aem.modernize.policy.PolicyImportRuleFound";
  private static final String NOT_FOUND_SERVICE_ID = "com.adobe.aem.modernize.policy.PolicyImportRuleNotFound";

  public final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  private final String[] RULE_PATHS = new String[] { "/apps/aem-modernize/policy/rules", "/apps/customer/policy/rules" };

  private final PolicyImportRuleService policyImportRuleService = new PolicyImportRuleServiceImpl();

  @Mocked
  private PolicyImportRule matchedImportRule;

  @Mocked
  private PolicyImportRule notMatchedImportRule;

  @BeforeEach
  public void beforeEach() {

    new Expectations() {{
      matchedImportRule.getId();
      result = FOUND_SERVICE_ID;
      notMatchedImportRule.getId();
      result = NOT_FOUND_SERVICE_ID;
    }};
  
    for (String path : RULE_PATHS) {
      context.load().json("/policy/test-rules.json", path);
    }
    context.load().json("/policy/test-rules.json", "/apps/not-registered/policy/rules");
    context.load().json("/policy/all-designs.json", "/etc/designs/all");
    context.load().json("/policy/all-conf.json", CONF_PATH);

    Map<String, Object> props = new HashMap<>();
    props.put("search.paths", RULE_PATHS);
    context.registerService(PolicyImportRule.class, matchedImportRule);
    context.registerService(PolicyImportRule.class, notMatchedImportRule);
    context.registerInjectActivateService(policyImportRuleService, props);
  }

  @Test
  public <R extends ResourceResolver> void testShallowApply() throws Exception {

    Set<String> rules = new HashSet<>(Arrays.asList(
        "/apps/aem-modernize/policy/rules/simple",
        "/apps/aem-modernize/policy/rules/not-found",
        "/apps/not-registered/policy/rules/simple",
        FOUND_SERVICE_ID,
        "/apps/aem-modernize/policy/rules/rewriteOptional"
    ));

    final int[] callCounts = { 0, 0 };
    final boolean[] returns = { false, true };
    new MockUp<NodeBasedPolicyImportRule>() {

      private final List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(@NotNull Node root) {
        return returns[callCounts[0]++];
      }

      @Mock
      public Node applyTo(@NotNull Node root, @NotNull Set<Node> finalNodes) {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };

    ResourceResolver rr = context.resourceResolver();
    Resource source = context.resourceResolver().getResource("/etc/designs/all/jcr:content/homepage/title");
    assertTrue(policyImportRuleService.apply(source, CONF_PATH, rules, false), "Policy Applied");
    assertEquals(2, callCounts[0], "Correct number of matched called.");
    assertEquals(1, callCounts[1], "Correct number of apply called.");

    ValueMap vm = source.getValueMap();
    assertEquals("/conf/test/settings/wcm/policies/geometrixx/components/title/policy", vm.get(PolicyImportRuleService.PN_IMPORTED, String.class), "Imported property set.");
    Resource policy = rr.getResource("/conf/test/settings/wcm/policies/geometrixx/components/title/policy");
    vm = policy.getValueMap();
    assertNotNull(policy, "Policy Created");
    assertNotNull(vm.get(NameConstants.PN_TITLE, String.class), "Policy Name set");
    assertNotNull(vm.get(NameConstants.PN_DESCRIPTION, String.class), "Policy description set");
  }

  @Test
  public void testOverrideShallow() throws Exception {
    Set<String> rules = new HashSet<>(Arrays.asList(
        "/apps/aem-modernize/policy/rules/simple",
        "/apps/aem-modernize/policy/rules/not-found",
        "/apps/not-registered/policy/rules/simple",
        FOUND_SERVICE_ID,
        "/apps/aem-modernize/policy/rules/rewriteOptional"
    ));

    final int[] callCounts = { 0, 0 };
    final boolean[] returns = { false, true };
    new MockUp<NodeBasedPolicyImportRule>() {

      private final List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(@NotNull Node root) {
        return returns[callCounts[0]++];
      }

      @Mock
      public Node applyTo(@NotNull Node root, @NotNull Set<Node> finalNodes) {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };

    ResourceResolver rr = context.resourceResolver();
    Resource source = rr.getResource("/etc/designs/all/jcr:content/homepage/par/title");
    assertTrue(policyImportRuleService.apply(source, CONF_PATH, rules, true), "Policy Applied");
    assertEquals(2, callCounts[0], "Correct number of matched called.");
    assertEquals(1, callCounts[1], "Correct number of apply called.");
    ValueMap vm = source.getValueMap();
    assertEquals("/conf/test/settings/wcm/policies/geometrixx/components/title/policy_1234", vm.get(PolicyImportRuleService.PN_IMPORTED, String.class), "Imported property set.");
    Resource policy = rr.getResource("/conf/test/settings/wcm/policies/geometrixx/components/title/policy_1234");
    assertNotNull(policy, "Policy Created");
    vm = policy.getValueMap();
    assertNotNull(vm.get(NameConstants.PN_TITLE, String.class), "Policy Name set");
    assertNotNull(vm.get(NameConstants.PN_DESCRIPTION, String.class), "Policy description set");
    

  }

  @Test
  public void testNotOverriddenShallow() throws RewriteException {
    Set<String> rules = new HashSet<>(Arrays.asList(
        "/apps/aem-modernize/policy/rules/simple",
        "/apps/aem-modernize/policy/rules/not-found",
        "/apps/not-registered/policy/rules/simple",
        FOUND_SERVICE_ID,
        "/apps/aem-modernize/policy/rules/rewriteOptional"
    ));

    final int[] callCounts = { 0, 0 };
    final boolean[] returns = { false, true };
    new MockUp<NodeBasedPolicyImportRule>() {

      private final List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(@NotNull Node root) {
        return returns[callCounts[0]++];
      }

      @Mock
      public Node applyTo(@NotNull Node root, @NotNull Set<Node> finalNodes) {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };

    ResourceResolver rr = context.resourceResolver(); 
    Resource source = rr.getResource("/etc/designs/all/jcr:content/homepage/par/title");
    assertFalse(policyImportRuleService.apply(source, CONF_PATH, rules, false), "Policy Not Applied");
    assertEquals(0, callCounts[0], "Correct number of matched called.");
    assertEquals(0, callCounts[1], "Correct number of apply called.");
  }

  @Test
  public void testDeepApply() throws RewriteException {
    final boolean[] called = { false, false };
    Set<String> rules = new HashSet<>(Arrays.asList(
        "/apps/aem-modernize/component/rules/simple",
        "/apps/aem-modernize/component/rules/not-found",
        "/apps/not-registered/component/rules/simple",
        "com.adobe.aem.modernize.component.ComponentRewriteRuleFound",
        "/apps/aem-modernize/component/rules/rewriteOptional"
    ));
    new MockUp<PolicyTreeImporter>() {

      @Mock
      public Style importStyles(Style root, List<RewriteRule> rules, boolean overwrite) {
        called[1] = true;
        assertNotNull(root);
        assertNotNull(rules);
        assertFalse(overwrite);
        return root;
      }
    };

    Resource source = context.resourceResolver().getResource("/etc/designs/all/jcr:content/homepage");
    policyImportRuleService.apply(source, CONF_PATH, rules, true, false);
  }

}

