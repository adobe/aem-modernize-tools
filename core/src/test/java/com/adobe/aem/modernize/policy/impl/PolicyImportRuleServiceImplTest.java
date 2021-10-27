package com.adobe.aem.modernize.policy.impl;

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
import com.day.cq.wcm.api.designer.Cell;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Style;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import lombok.experimental.Delegate;
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

  public final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  private final String[] RULE_PATHS = new String[] { "/apps/aem-modernize/policy/rules", "/apps/customer/policy/rules" };

  private final PolicyImportRuleService policyImportRuleService = new PolicyImportRuleServiceImpl();

  @Mocked
  private PolicyImportRule matchedImportRule;

  @Mocked
  private PolicyImportRule notMatchedImportRule;

  @BeforeEach
  public void beforeEach() {
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
    final boolean[] returns = { false, true};
    new MockUp<NodeBasedPolicyImportRule>() {

      private final List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(Node root) {
        return returns[callCounts[0]++];
      }

      @Mock
      public Node applyTo(Node root, Set<Node> finalNodes) {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };


    new Expectations() {{
      matchedImportRule.getId();
      result = FOUND_SERVICE_ID;
    }};

    Resource source = context.resourceResolver().getResource("/etc/designs/all/jcr:content/homepage/title");
    policyImportRuleService.apply(source, CONF_PATH, rules, false, false);
    assertEquals(2, callCounts[0], "Correct number of matched called.");
    assertEquals(1, callCounts[1], "Correct number of apply called.");
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
    final boolean[] returns = { false, true};
    new MockUp<NodeBasedPolicyImportRule>() {

      private final List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(Node root) {
        return returns[callCounts[0]++];
      }

      @Mock
      public Node applyTo(Node root, Set<Node> finalNodes) {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };


    new Expectations() {{
      matchedImportRule.getId();
      result = FOUND_SERVICE_ID;
    }};

    Resource source = context.resourceResolver().getResource("/etc/designs/all/jcr:content/homepage/par/title");
    policyImportRuleService.apply(source, CONF_PATH, rules, false, true);
    assertEquals(2, callCounts[0], "Correct number of matched called.");
    assertEquals(1, callCounts[1], "Correct number of apply called.");
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
    final boolean[] returns = { false, true};
    new MockUp<NodeBasedPolicyImportRule>() {

      private final List<String> paths = new ArrayList<>();

      @Mock
      public void $init(@NotNull Node node) throws RepositoryException {
        assertNotNull(node);
        this.paths.add(node.getPath());
      }

      @Mock
      public boolean matches(Node root) {
        return returns[callCounts[0]++];
      }

      @Mock
      public Node applyTo(Node root, Set<Node> finalNodes) {
        callCounts[1]++;
        return root;
      }

      @Mock
      public int getRanking() {
        return Integer.MAX_VALUE;
      }
    };


    new Expectations() {{
      matchedImportRule.getId();
      result = FOUND_SERVICE_ID;
    }};

    Resource source = context.resourceResolver().getResource("/etc/designs/all/jcr:content/homepage/par/title");
    policyImportRuleService.apply(source, CONF_PATH, rules, false, false);
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

    new Expectations() {{
      matchedImportRule.getId();
      result = FOUND_SERVICE_ID;
    }};
    Resource source = context.resourceResolver().getResource("/etc/designs/all/jcr:content/homepage");
    policyImportRuleService.apply(source, CONF_PATH, rules, true, false);
  }

}

