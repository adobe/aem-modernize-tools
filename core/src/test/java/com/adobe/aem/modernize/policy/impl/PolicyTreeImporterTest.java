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
import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.rule.RewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import mockit.Expectations;
import mockit.Mocked;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.policy.PolicyImportRuleService.*;
import static com.adobe.aem.modernize.policy.impl.PolicyTreeImporter.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class PolicyTreeImporterTest {
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  private static final String CONF_PATH = "/conf/test";

  @Mocked
  private RewriteRule mockRule;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/policy/all-conf.json", CONF_PATH);
    context.load().json("/policy/all-designs.json", "/etc/designs/test");
  }

  @Test
  public void skipsFinal() throws Exception {
    final List<Node> nodes = new ArrayList<>();

    List<RewriteRule> rules = new ArrayList<>();
    rules.add(mockRule);
    rules.add(new FinalRewriteRule("/etc/designs/test/jcr:content/homepage/rightpar"));

    new Expectations() {{
      mockRule.matches(withCapture(nodes));
    }};

    ResourceResolver rr = context.resourceResolver();
    Resource root = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage");

    PolicyTreeImporter.importStyles(root, CONF_PATH, rules, false);

    assertEquals(4, nodes.size(), "Node matches call count");
    for (Node n : nodes) {
      assertNotEquals("/etc/designs/test/jcr:content/homepage/rightpar/title", n.getPath(), "Final path check");
    }
    String policyPath = PathUtils.concat(CONF_PATH, POLICY_REL_PATH, "foundation/components/iparsys/policy");
    Resource policy = context.resourceResolver().getResource(policyPath);
    assertNotNull(policy, "Policy copied");
    assertEquals(POLICY_RESOURCE_TYPE, policy.getResourceType(), "Policy resource type.");
  }

  @Test
  public void setsMeta() throws Exception {

    final List<Node> nodes = new ArrayList<>();
    List<RewriteRule> rules = new ArrayList<>();
    rules.add(mockRule);
    rules.add(new MappedRewriteRile("/etc/designs/test/jcr:content/homepage/par/title"));

    new Expectations() {{
      mockRule.matches(withCapture(nodes));
    }};

    Resource root = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage");

    PolicyTreeImporter.importStyles(root, CONF_PATH, rules, true);

    String policyPath = PathUtils.concat(CONF_PATH, POLICY_REL_PATH, "geometrixx/components/title/policy");
    Resource policy = context.resourceResolver().getResource(policyPath);
    assertNotNull(policy, "Policy copied");
    assertEquals(POLICY_RESOURCE_TYPE, policy.getResourceType(), "Policy resource type.");
    Node written = policy.adaptTo(Node.class);
    assertEquals("Imported (/etc/designs/test/jcr:content/homepage/par/title)", written.getProperty(NameConstants.PN_TITLE).getString(), "Title set");
    assertEquals("Imported from: /etc/designs/test/jcr:content/homepage/par/title", written.getProperty(NameConstants.PN_DESCRIPTION).getString(), "Title set");
    assertEquals(6, nodes.size(), "Node matches call count");
    Node original = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage/par/title").adaptTo(Node.class);
    assertEquals(policyPath, original.getProperty(PN_IMPORTED).getString(), "Imported property set.");
  }

  @Test
  public void skipsSetMeta() throws Exception {

    final List<Node> nodes = new ArrayList<>();
    List<RewriteRule> rules = new ArrayList<>();
    rules.add(mockRule);
    rules.add(new MappedRewriteRile("/etc/designs/test/jcr:content/homepage/rightpar/title"));

    new Expectations() {{
      mockRule.matches(withCapture(nodes));
    }};

    Resource root = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage");

    PolicyTreeImporter.importStyles(root, CONF_PATH, rules, false);

    Resource policy = context.resourceResolver().getResource(PathUtils.concat(CONF_PATH, POLICY_REL_PATH, "geometrixx/components/title/policy"));
    assertNotNull(policy, "Policy copied");
    assertEquals(POLICY_RESOURCE_TYPE, policy.getResourceType(), "Policy resource type.");
    Node written = policy.adaptTo(Node.class);
    assertNotEquals("Imported (/etc/designs/test/jcr:content/homepage/rightpar/title)", written.getProperty(NameConstants.PN_TITLE).getString(), "Title set");
    assertNotEquals("Imported from: /etc/designs/test/jcr:content/homepage/rightpar/title", written.getProperty(NameConstants.PN_DESCRIPTION).getString(), "Title set");
    assertEquals(5, nodes.size(), "Node matches call count");
  }

  @Test
  public void skipsImported() throws Exception {
    final List<Node> nodes = new ArrayList<>();
    List<RewriteRule> rules = new ArrayList<>();
    rules.add(mockRule);

    new Expectations() {{
      mockRule.matches(withCapture(nodes));
    }};

    Resource root = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage");
    PolicyTreeImporter.importStyles(root, CONF_PATH, rules, false);

    assertEquals(5, nodes.size(), "Node matches call count");
    for (Node n : nodes) {
      assertNotEquals("/etc/designs/test/jcr:content/homepage/part/title", n.getPath(), "Imported path check");
    }
    Resource policy = context.resourceResolver().getResource(PathUtils.concat(CONF_PATH, POLICY_REL_PATH, "geometrixx/components/title/policy"));
    assertNull(policy, "Policy not copied");
  }

  @Test
  public void overridesImported() throws Exception {
    final List<Node> nodes = new ArrayList<>();
    List<RewriteRule> rules = new ArrayList<>();
    rules.add(mockRule);

    new Expectations() {{
      mockRule.matches(withCapture(nodes));
    }};

    Resource root = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage");
    PolicyTreeImporter.importStyles(root, CONF_PATH, rules, true);

    assertEquals(6, nodes.size(), "Node matches call count");

    boolean overwritten = false;
    for (Node n : nodes) {
      if (StringUtils.equals("/etc/designs/test/jcr:content/homepage/par/title", n.getPath())) {
        overwritten = true;
        break;
      }
    }
    assertTrue(overwritten, "Override path check");

  }

  private static class FinalRewriteRule implements RewriteRule {

    private final String path;

    private FinalRewriteRule(String path) {
      this.path = path;
    }

    @Override
    public String getId() {
      return path;
    }

    @Override
    public boolean matches(@NotNull Node root) throws RepositoryException {
      return root.getPath().equals(path);
    }

    @Override
    public Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RepositoryException {
      NodeIterator it = root.getNodes();
      finalPaths.add(path);
      while (it.hasNext()) {
        finalPaths.add(it.nextNode().getPath());
      }

      return root;
    }
  }

  private static class MappedRewriteRile implements RewriteRule {

    private final String srcPath;

    private MappedRewriteRile(String src) {
      this.srcPath = src;
    }

    @Override
    public String getId() {
      return srcPath;
    }

    @Override
    public boolean matches(@NotNull Node root) throws RepositoryException {
      return root.getPath().equals(srcPath);
    }

    @Override
    public Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RepositoryException {
      return JcrUtil.copy(root, root.getParent(), "copyOfNode");
    }
  }
}
