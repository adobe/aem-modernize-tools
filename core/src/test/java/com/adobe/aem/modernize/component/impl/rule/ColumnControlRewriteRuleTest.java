package com.adobe.aem.modernize.component.impl.rule;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.day.cq.wcm.api.NameConstants;
import mockit.Mock;
import mockit.MockUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.component.impl.rule.ColumnControlRewriteRule.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ColumnControlRewriteRuleTest {

  // Oak needed to verify order preservation.
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  private void checkResponsive(Node node) throws Exception {
      Node responsive = node.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
      Node responsiveEntry = responsive.getNode("default");
      assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
      assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
      responsiveEntry = responsive.getNode("tablet");
      assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
      assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
      responsiveEntry = responsive.getNode("phone");
      assertEquals("12", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
      assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
  }

  @BeforeEach
  public void beforeEach() {
    context.load().json("/component/page-content.json", "/content/test");
    context.load().json("/component/code-content.json", "/apps");
  }

  @Test
  public void activation() {

    // Negative tests
    final Map<String, Object> props = new HashMap<>();
    props.put("grid.type", "CONTAINER");
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new ColumnControlRewriteRule(), props), "Missing Container Resource Type");

    props.clear();
    props.put("container.resourceType", "geodemo/components/container");
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new ColumnControlRewriteRule(), props), "Missing layout");

    props.clear();
    props.put("layout.value", "foo;cq-colctrl-lt0");
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new ColumnControlRewriteRule(), props), "Wrong layout format.");

    props.clear();
    props.put("layout.value", "2;cq-colctrl-lt0");
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new ColumnControlRewriteRule(), props), "Missing widths");

    props.clear();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new Object[] { "notlong", 5L });
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new ColumnControlRewriteRule(), props), "Widths wrong types.");

    props.clear();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3]", "tablet=[notLong,6]" });
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new ColumnControlRewriteRule(), props), "Widths wrong types.");

    props.clear();
    props.put("layout.value", "3;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3]", "tablet=[6,6]", "phone=[12,12]" });
    assertThrows(RuntimeException.class, () -> context.registerInjectActivateService(new ColumnControlRewriteRule(), props), "Incorrect width count.");

    // Defaults
    props.clear();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3]", "tablet=[6,6]", "phone=[12,12]" });
    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    context.registerInjectActivateService(rule, props);
    assertEquals(20, rule.getRanking());
    assertFalse(StringUtils.isBlank(rule.getId()));
    assertEquals("ColumnControlRewriteRule ('2;cq-colctrl-lt0' => default=[3,3],phone=[12,12],tablet=[6,6])", rule.getTitle());

  }
  
  @Test
  public <F extends ResourceResolverFactory> void findMatches() {

    new MockUp<F>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
    };

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    Resource root = context.resourceResolver().getResource("/content/test");
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] { "default=[6,6]" });
    context.registerInjectActivateService(rule, props);

    Set<String> matched = rule.findMatches(root);
    assertEquals(9, matched.size(), "Found count");
    assertTrue(matched.contains("/content/test/matches/jcr:content/par"));
    assertTrue(matched.contains("/content/test/matches/jcr:content/rightpar"));
    assertTrue(matched.contains("/content/test/doesNotMatch/jcr:content/par"));
    assertTrue(matched.contains("/content/test/fourColumns/jcr:content/par"));
    assertTrue(matched.contains("/content/test/parentNotResponsiveGrid/jcr:content/par"));
    assertTrue(matched.contains("/content/test/parentNotResponsiveGrid/jcr:content/rightpar"));
    assertTrue(matched.contains("/content/test/extraFirstColumn/jcr:content/par"));
    assertTrue(matched.contains("/content/test/extraSecondColumn/jcr:content/par"));
    assertTrue(matched.contains("/content/test/extraMiddleColumns/jcr:content/par"));
  }

  @Test
  public void hasPattern() {
    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] { "default=[6,6]" });
    context.registerInjectActivateService(rule, props);

    assertTrue(rule.hasPattern( "foundation/components/parsys"), "Has pattern");
    assertTrue(rule.hasPattern( "wcm/foundation/components/responsivegrid"), "Does not have pattern");
    assertFalse(rule.hasPattern("foundation/components/parsys/colctrl", "Does not have pattern"));
  }

  @Test
  public <R extends ResourceResolver, F extends ResourceResolverFactory> void matches() throws Exception {

    new MockUp<F>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
    };

    new MockUp<R>() {
      @Mock
      public void close() {}
    };

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] { "default=[6,6]" });
    context.registerInjectActivateService(rule, props);

    Node node = context.resourceResolver().getResource("/content/test/doesNotMatch/jcr:content").adaptTo(Node.class);
    assertFalse(rule.matches(node), "Does not match resource type");

    node = context.resourceResolver().getResource("/content/test/fourColumns/jcr:content/par").adaptTo(Node.class);
    assertFalse(rule.matches(node), "Does not match layout");

    node = context.resourceResolver().getResource("/content/test/parentNotResponsiveGrid/jcr:content/par").adaptTo(Node.class);
    assertFalse(rule.matches(node), "Does not match when parent isn't responsive grid");

    node = context.resourceResolver().getResource("/content/test/matches/jcr:content/par").adaptTo(Node.class);
    assertTrue(rule.matches(node), "Matches all rules");

  }

  @Test
  public <R extends ResourceResolver, F extends ResourceResolverFactory>  void responsiveGridEqual() throws Exception {

    new MockUp<F>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
    };
    new MockUp<R>() {
      @Mock
      public void close() {}
    };

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3]", "tablet=[6,6]", "phone=[12,12]" });
    props.put("container.resourceType", "geodemo/components/container");
    context.registerInjectActivateService(rule, props);

    Node node = context.resourceResolver().getResource("/content/test/matches/jcr:content/par").adaptTo(Node.class);
    assertTrue(rule.matches(node), "Match before run");

    Set<String> finalPaths = new HashSet<>();
    Session session = node.getSession();

    rule.applyTo(node, finalPaths);
    session.save();
    assertTrue(finalPaths.isEmpty(), "No final paths set.");

    NodeIterator siblings = node.getNodes();
    assertEquals("title", siblings.nextNode().getName(), "Node Order preserved");
    Node item = siblings.nextNode();
    assertEquals("image_1", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("image_0", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("title_1", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("title_2", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_1", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_0", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("image", item.getName(), "Node Order preserved");
    assertFalse(siblings.hasNext(), "No more nodes.");

  }

  @Test
  public <R extends ResourceResolver, F extends ResourceResolverFactory>  void responsiveGridExtraFirst() throws Exception {

    new MockUp<F>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
    };
    new MockUp<R>() {
      @Mock
      public void close() {}
    };

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3]", "tablet=[6,6]", "phone=[12,12]" });
    props.put("container.resourceType", "geodemo/components/container");
    context.registerInjectActivateService(rule, props);

    Node node = context.resourceResolver().getResource("/content/test/extraFirstColumn/jcr:content/par").adaptTo(Node.class);
    assertTrue(rule.matches(node), "Match before run");

    Set<String> finalPaths = new HashSet<>();
    Session session = node.getSession();

    rule.applyTo(node, finalPaths);
    session.save();
    assertTrue(finalPaths.isEmpty(), "No final paths set.");

    NodeIterator siblings = node.getNodes();
    assertEquals("title", siblings.nextNode().getName(), "Node Order preserved");
    Node item = siblings.nextNode();
    assertEquals("image_1", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("image_0", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("title_1", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("title_2", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_1", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_0", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_extra0", item.getName(), "Node Order Correct");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_extra1", item.getName(), "Node Order Correct");
    checkResponsive(item);
    assertEquals("newline", item.getProperty("cq:responsive/default/behavior").getString(), "Behavior correct.");


    item = siblings.nextNode();
    assertEquals("image", item.getName(), "Node Order preserved");
    assertFalse(siblings.hasNext(), "No more nodes.");
  }

  @Test
  public <R extends ResourceResolver, F extends ResourceResolverFactory>  void responsiveGridExtraSecond() throws Exception {

    new MockUp<F>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
    };
    new MockUp<R>() {
      @Mock
      public void close() {}
    };

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[6,6]", "tablet=[6,6]", "phone=[6,6]" });
    props.put("container.resourceType", "geodemo/components/container");
    context.registerInjectActivateService(rule, props);

    Node node = context.resourceResolver().getResource("/content/test/extraSecondColumn/jcr:content/par").adaptTo(Node.class);
    assertTrue(rule.matches(node), "Match before run");

    Set<String> finalPaths = new HashSet<>();
    Session session = node.getSession();

    rule.applyTo(node, finalPaths);
    session.save();
    assertTrue(finalPaths.isEmpty(), "No final paths set.");

    NodeIterator siblings = node.getNodes();
    assertEquals("title", siblings.nextNode().getName(), "Node Order preserved");
    Node item = siblings.nextNode();
    assertEquals("image_1", item.getName(), "Node Order Correct");
    Node responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    Node responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("image_0", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("title_1", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("title_2", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("text_1", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("text_0", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");


    item = siblings.nextNode();
    assertEquals("text_extra0", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("6", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("6", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("6", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");


    item = siblings.nextNode();
    assertEquals("text_extra1", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("6", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("6", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("6", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");


    item = siblings.nextNode();
    assertEquals("image", item.getName(), "Node Order preserved");
    assertFalse(siblings.hasNext(), "No more nodes.");
  }


  @Test
  public <R extends ResourceResolver, F extends ResourceResolverFactory>  void responsiveGridExtraMiddle() throws Exception {

    new MockUp<F>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
    };
    new MockUp<R>() {
      @Mock
      public void close() {}
    };

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "4;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3,3,3]", "tablet=[3,3,3,3]", "phone=[3,3,3,3]" });
    props.put("container.resourceType", "geodemo/components/container");
    context.registerInjectActivateService(rule, props);

    Node node = context.resourceResolver().getResource("/content/test/extraMiddleColumns/jcr:content/par").adaptTo(Node.class);
    assertTrue(rule.matches(node), "Match before run");

    Set<String> finalPaths = new HashSet<>();
    Session session = node.getSession();

    rule.applyTo(node, finalPaths);
    session.save();
    assertTrue(finalPaths.isEmpty(), "No final paths set.");

    NodeIterator siblings = node.getNodes();
    assertEquals("title", siblings.nextNode().getName(), "Node Order preserved");
    Node item = siblings.nextNode();

    assertEquals("text_0", item.getName(), "Node Order Correct");
    Node responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    Node responsiveEntry = responsive.getNode("default");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("text_1", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("text_2", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("text_3", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("text_extra0", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("3", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("3", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("3", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");

    item = siblings.nextNode();
    assertEquals("text_extra1", item.getName(), "Node Order Correct");
    responsive = item.getNode(NameConstants.NN_RESPONSIVE_CONFIG);
    responsiveEntry = responsive.getNode("default");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("3", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("tablet");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("3", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    responsiveEntry = responsive.getNode("phone");
    assertEquals("3", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
    assertEquals("3", responsiveEntry.getProperty(PN_OFFSET).getString(), "Offset set");
    assertEquals("newline", item.getProperty("cq:responsive/default/behavior").getString(), "Behavior correct.");

    item = siblings.nextNode();
    assertEquals("image", item.getName(), "Node Order preserved");
    assertFalse(siblings.hasNext(), "No more nodes.");
  }

  @Test
  public <R extends ResourceResolver, F extends ResourceResolverFactory> void container() throws Exception {

    final String containerResourceType = "geodemo/components/container";
    new MockUp<F>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
    };
    new MockUp<R>() {
      @Mock
      public void close() {}
    };

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "4;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3,3,3]", "tablet=[6,6,6,6]", "phone=[12,12,12,12]" });
    props.put("grid.type", "CONTAINER");
    props.put("container.resourceType", "geodemo/components/container");
    context.registerInjectActivateService(rule, props);

    Node node = context.resourceResolver().getResource("/content/test/fourColumns/jcr:content/par").adaptTo(Node.class);
    assertTrue(rule.matches(node), "Match before run");

    Set<String> finalPaths = new HashSet<>();
    node = rule.applyTo(node, finalPaths);
    node.getSession().save();

    // All containers should be final
    assertEquals(4, finalPaths.size(), "Final paths length.");
    assertTrue(finalPaths.contains("/content/test/fourColumns/jcr:content/par/container"), "Final paths has container");
    assertTrue(finalPaths.contains("/content/test/fourColumns/jcr:content/par/container0"), "Final paths has container");
    assertTrue(finalPaths.contains("/content/test/fourColumns/jcr:content/par/container1"), "Final paths has container");
    assertTrue(finalPaths.contains("/content/test/fourColumns/jcr:content/par/container2"), "Final paths has container");

    NodeIterator siblings = node.getNodes();
    assertEquals("title", siblings.nextNode().getName(), "Node Order preserved");

    Node container = siblings.nextNode();
    assertEquals("container", container.getName(), "Node Order preserved");
    assertEquals(containerResourceType, container.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Resource type matches");

    NodeIterator contents = container.getNodes();
    checkResponsive(container);
    contents.nextNode();
    assertEquals("image_1", contents.nextNode().getName(), "Container content order.");
    assertEquals("title_1", contents.nextNode().getName(), "Container content order.");


    container = siblings.nextNode();
    assertEquals("container0", container.getName(), "Node Order preserved");
    assertEquals(containerResourceType, container.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Resource type matches");
    contents = container.getNodes();
    checkResponsive(container);
    contents.nextNode();
    assertEquals("text_1", contents.nextNode().getName(), "Container content order.");

    container = siblings.nextNode();
    assertEquals("container1", container.getName(), "Node Order preserved");
    assertEquals(containerResourceType, container.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Resource type matches");
    contents = container.getNodes();
    checkResponsive(container);
    contents.nextNode();
    assertEquals("image_0", contents.nextNode().getName(), "Container content order.");
    assertEquals("title_2", contents.nextNode().getName(), "Container content order.");

    container = siblings.nextNode();
    assertEquals("container2", container.getName(), "Node Order preserved");
    assertEquals(containerResourceType, container.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Resource type matches");
    contents = container.getNodes();
    checkResponsive(container);
    contents.nextNode();
    assertEquals("text_0", contents.nextNode().getName(), "Container content order.");

    assertEquals("image", siblings.nextNode().getName(), "Node Order preserved");
    assertFalse(siblings.hasNext(), "No more nodes.");
  }

}
