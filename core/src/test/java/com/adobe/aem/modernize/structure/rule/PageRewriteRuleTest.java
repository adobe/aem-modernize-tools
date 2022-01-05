package com.adobe.aem.modernize.structure.rule;

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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.structure.rule.PageRewriteRule.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class PageRewriteRuleTest {

  private static final String STATIC_TEMPLATE = "/apps/aem-modernize/templates/homepage";
  private static final String SLING_RESOURCE_TYPE = "aem-modernize/components/homepage";
  private static final String EDITABLE_TEMPLATE = "/conf/aem-modernize/settings/wcm/templates/aem-modernize-home-page";
  private static final String CONTAINER_RESOURCE_TYPE = "aem-modernize/components/container";
  private static final String[] ORDER = {
      "container:header",
      "title",
      "container",
      "container_12345",
      "another/nested:bar"
  };
  private static final String[] REMOVE = { "toBeRemoved" };
  private static final String[] RENAME = { "par=container/container", "rightpar=container_12345", "ignored=", "alsoignored" };
  private static final String[] IGNORED = { "doNotTouch" };

  public final AemContext context = new AemContext(ResourceResolverType.JCR_OAK);

  @BeforeEach
  public void beforeEach() throws Exception {
    context.load().json("/structure/page-content.json", "/content/test");
    context.load().json("/structure/conf-template.json", "/conf/aem-modernize/settings/wcm/templates/aem-modernize-home-page");
  }

  @Test
  public void testActivate() throws Exception {

    // No Static template
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new PageRewriteRule(), new HashMap<>()));

    // No Sling Resource Type
    final Map<String, Object> props = new HashMap<>();
    props.put("static.template", STATIC_TEMPLATE);
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new PageRewriteRule(), props));

    // No Editable Template
    props.clear();
    props.put("static.template", STATIC_TEMPLATE);
    props.put("sling.resourceType", SLING_RESOURCE_TYPE);
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new PageRewriteRule(), props));


    // No Container resource Type
    props.clear();
    props.put("static.template", STATIC_TEMPLATE);
    props.put("sling.resourceType", SLING_RESOURCE_TYPE);
    props.put("editable.template", EDITABLE_TEMPLATE);
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new PageRewriteRule(), props));

    // All required
    final PageRewriteRule rule = new PageRewriteRule();
    props.clear();
    props.put("static.template", STATIC_TEMPLATE);
    props.put("sling.resourceType", SLING_RESOURCE_TYPE);
    props.put("editable.template", EDITABLE_TEMPLATE);
    props.put("container.resourceType", CONTAINER_RESOURCE_TYPE);
    context.registerInjectActivateService(rule, props);


    // Parses Order
    props.put("order.components", ORDER);
    context.registerInjectActivateService(rule, props);

    Field f = rule.getClass().getDeclaredField("componentOrdering");
    f.setAccessible(true);
    Map<String, List<String>> orderProp = (Map<String, List<String>>) f.get(rule);

    List<String> compList = orderProp.get(NN_ROOT_CONTAINER);
    assertEquals(3, compList.size(), "Root container list");
    assertEquals("title", compList.get(0), "Root container order");
    assertEquals("container", compList.get(1), "Root container order");
    assertEquals("container_12345", compList.get(2), "Root container order");

    compList = orderProp.get(PathUtils.concatRelativePaths(NN_ROOT_CONTAINER, "container"));
    assertEquals(1, compList.size(), "Authorable container list");
    assertEquals("header", compList.get(0), "Authorable container order");

    // Parses Removals
    props.put("remove.components", REMOVE);
    context.registerInjectActivateService(rule, props);
    f = rule.getClass().getDeclaredField("componentsToRemove");
    f.setAccessible(true);
    List<String> removed = (List<String>) f.get(rule);
    assertEquals("toBeRemoved", removed.get(0), "Removed list");

    // Parses Renames
    props.put("rename.components", RENAME);
    context.registerInjectActivateService(rule, props);
    f = rule.getClass().getDeclaredField("componentRenamed");
    f.setAccessible(true);
    Map<String, String> renamed = (Map<String, String>) f.get(rule);
    assertEquals("container/container", renamed.get("par"), "Renamed container");
    assertEquals("container_12345", renamed.get("rightpar"), "Renamed container");
    assertNull(renamed.get("ignored"), "Ignores invalid");
    assertNull(renamed.get("alsoignored"), "Ignores invalid");

    // Parses Ignores
    props.put("ignore.components", IGNORED);
    context.registerInjectActivateService(rule, props);
    f = rule.getClass().getDeclaredField("componentsToIgnore");
    f.setAccessible(true);
    List<String> ignored = (List<String>) f.get(rule);
    assertEquals("doNotTouch", ignored.get(0), "Ignored list");

    assertEquals(10, rule.getRanking(), "Rule ranking.");
    assertEquals(PageRewriteRule.class.getName(), rule.getId(), "Default Rule Id");
    assertEquals("PageRewriteRule (/apps/aem-modernize/templates/homepage -> /conf/aem-modernize/settings/wcm/templates/aem-modernize-home-page)", rule.getTitle());

    props.put("service.pid", PageRewriteRule.class.getName() + "~customrule");
    context.registerInjectActivateService(rule, props);
    assertEquals(PageRewriteRule.class.getName() + "~customrule", rule.getId(), "ServicePID Rule ID");
  }

  @Test
  public void hasPattern() {
    final PageRewriteRule rule = new PageRewriteRule();
    Map<String, Object> props = new HashMap<>();
    props.put("static.template", STATIC_TEMPLATE);
    props.put("sling.resourceType", SLING_RESOURCE_TYPE);
    props.put("editable.template", EDITABLE_TEMPLATE);
    props.put("container.resourceType", CONTAINER_RESOURCE_TYPE);
    props.put("order.components", ORDER);
    props.put("remove.components", REMOVE);
    props.put("rename.components", RENAME);
    context.registerInjectActivateService(rule, props);

    assertTrue(rule.hasPattern("aem-modernize/components/homepage"), "Pattern Found");
    assertFalse(rule.hasPattern("aem-modernize/components/page"), "Pattern Not Found");
  }

  @Test
  public void findMatches() {

    final PageRewriteRule rule = new PageRewriteRule();
    Map<String, Object> props = new HashMap<>();
    props.put("static.template", STATIC_TEMPLATE);
    props.put("sling.resourceType", SLING_RESOURCE_TYPE);
    props.put("editable.template", EDITABLE_TEMPLATE);
    props.put("container.resourceType", CONTAINER_RESOURCE_TYPE);
    props.put("order.components", ORDER);
    props.put("remove.components", REMOVE);
    props.put("rename.components", RENAME);
    context.registerInjectActivateService(rule, props);

    String path = "/content/test/matches";
    ResourceResolver rr = context.resourceResolver();
    Resource page = rr.getResource(path);
    Set<String> matches = rule.findMatches(page);
    assertEquals(1, matches.size(), "Matches length");
    assertTrue(matches.contains(path), "Matches content.");

    path = "/content/test";
    page = rr.getResource(path);
    matches = rule.findMatches(page);
    assertEquals(0, matches.size(), "Matches length");

    path = "/content/test/doesNotMatch";
    page = rr.getResource(path);
    matches = rule.findMatches(page);
    assertEquals(0, matches.size(), "Matches length");
  }

  @Test
  public void matches() throws Exception {
    final PageRewriteRule rule = new PageRewriteRule();
    Map<String, Object> props = new HashMap<>();
    props.put("static.template", STATIC_TEMPLATE);
    props.put("sling.resourceType", SLING_RESOURCE_TYPE);
    props.put("editable.template", EDITABLE_TEMPLATE);
    props.put("container.resourceType", CONTAINER_RESOURCE_TYPE);
    context.registerInjectActivateService(rule, props);

    ResourceResolver rr = context.resourceResolver();
    // Test page root
    Node node = rr.getResource("/content/test/matches").adaptTo(Node.class);
    assertTrue(rule.matches(node), "cq:Page matches");

    // Page Content
    node = rr.getResource("/content/test/matches/jcr:content").adaptTo(Node.class);
    assertTrue(rule.matches(node), "cq:PageContent matches");

    // Doesn't match
    // Test page root
    node = rr.getResource("/content/test/doesNotMatch").adaptTo(Node.class);
    assertFalse(rule.matches(node), "cq:Page matches");

    // Page Content
    node = rr.getResource("/content/test/doesNotMatch/jcr:content").adaptTo(Node.class);
    assertFalse(rule.matches(node), "cq:PageContent matches");

  }

  @Test
  public void applyTo() throws Exception {
    final PageRewriteRule rule = new PageRewriteRule();
    Map<String, Object> props = new HashMap<>();
    props.put("static.template", STATIC_TEMPLATE);
    props.put("sling.resourceType", SLING_RESOURCE_TYPE);
    props.put("editable.template", EDITABLE_TEMPLATE);
    props.put("container.resourceType", CONTAINER_RESOURCE_TYPE);
    props.put("order.components", ORDER);
    props.put("remove.components", REMOVE);
    props.put("rename.components", RENAME);
    props.put("ignore.components", IGNORED);
    context.registerInjectActivateService(rule, props);

    ResourceResolver rr = context.resourceResolver();
    Node node = rr.getResource("/content/test/matches").adaptTo(Node.class);

    Set<String> finalNodes = new HashSet<>();
    Node rewrittenNode = rule.applyTo(node, finalNodes);
    assertFalse(rewrittenNode.hasProperty("cq:designPath"), "Design path removed");
    assertEquals(EDITABLE_TEMPLATE, rewrittenNode.getProperty("cq:template").getString(), "CQ Template property");
    assertEquals("aem-modernize/components/structure/homepage", rewrittenNode.getProperty("sling:resourceType").getString(), "Sling Resource Type");

    assertTrue(rewrittenNode.hasNode("cq:LiveSyncConfig"), "Live Sync Config ignored");
    assertTrue(rewrittenNode.hasNode("cq:BlueprintSyncConfig"), "Blueprint Sync Config ignored");
    assertTrue(rewrittenNode.hasNode("doNotTouch"), "Explicitly specified node ignored");

    Node rootContainer = rewrittenNode.getNode("root");
    assertNotNull(rootContainer, "Root Container");
    assertTrue(rootContainer.getPrimaryNodeType().isNodeType(NodeType.NT_UNSTRUCTURED), "Container node type");

    assertEquals(CONTAINER_RESOURCE_TYPE, rootContainer.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Root container resource type");
    NodeIterator children = rootContainer.getNodes();
    assertEquals("title", children.nextNode().getName(), "Root container node order");
    assertEquals("container", children.nextNode().getName(), "Root container node order");
    assertEquals("container_12345", children.nextNode().getName(), "Root container node order");
    assertEquals("movedToEnd", children.nextNode().getName(), "Root container node order.");
    assertEquals("another", children.nextNode().getName(), "Root container node order.");

    assertFalse(children.hasNext(), "Root container node order.");

    Node container = rootContainer.getNode("container");
    children = container.getNodes();
    assertEquals("header", children.nextNode().getName(), "Container node order");
    assertEquals("container", children.nextNode().getName(), "Container node order");
    assertFalse(children.hasNext(), "Container node order");

    container = rootContainer.getNode("another/nested");
    children = container.getNodes();
    assertEquals("bar", children.nextNode().getName(), "Nested moves.");
    assertFalse(children.hasNext(), "Nested expected children.");
  }

}
