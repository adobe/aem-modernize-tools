package com.adobe.aem.modernize.component.impl.rule;

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
      assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Width set");
      responsiveEntry = responsive.getNode("tablet");
      assertEquals("6", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
      assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Width set");
      responsiveEntry = responsive.getNode("phone");
      assertEquals("12", responsiveEntry.getProperty(PN_WIDTH).getString(), "Width set");
      assertEquals("0", responsiveEntry.getProperty(PN_OFFSET).getString(), "Width set");
  }

  @BeforeEach
  protected void beforeEach() {
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
    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
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
    context.registerInjectActivateService(rule, props);
    assertEquals(20, rule.getRanking());
    assertFalse(StringUtils.isBlank(rule.getId()));
    assertEquals("ColumnControlRewriteRule ('2;cq-colctrl-lt0' => default=[3,3],phone=[12,12],tablet=[6,6])", rule.getTitle());

  }
  
  @Test
  public void findMatches() {
    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    Resource root = context.resourceResolver().getResource("/content/test");
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] { "default=[6,6]" });
    context.registerInjectActivateService(rule, props);

    Set<String> matched = rule.findMatches(root);
    assertEquals(2, matched.size(), "Found count");
    assertTrue(matched.contains("/content/test/matches/jcr:content/par"));
    assertTrue(matched.contains("/content/test/parentNotResponsiveGrid/jcr:content/par"));
  }

  @Test
  public void hasPattern() {
    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    final Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] { "default=[6,6]" });
    context.registerInjectActivateService(rule, props);

    assertFalse(rule.hasPattern( "foundation/components/parsys"), "Does not have pattern");
    assertTrue(rule.hasPattern("foundation/components/parsys/colctrl", "Has pattern"));
  }

  @Test
  public <R extends ResourceResolverFactory> void matches() throws Exception {

    new MockUp<R>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
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
  public  <R extends ResourceResolverFactory>  void responsiveGrid() throws Exception {

    new MockUp<R>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
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
    assertEquals("image_1", item.getName(), "Node Order Preserved");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("title_1", item.getName(), "Node Order Preserved");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_1", item.getName(), "Node Order Preserved");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("image_0", item.getName(), "Node Order Preserved");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("title_2", item.getName(), "Node Order Preserved");
    checkResponsive(item);
    item = siblings.nextNode();
    assertEquals("text_0", item.getName(), "Node Order Preserved");
    checkResponsive(item);
    item = siblings.nextNode();

    assertEquals("image", item.getName(), "Node Order preserved");
    assertFalse(siblings.hasNext(), "No more nodes.");

  }

  @Test
  public <R extends ResourceResolverFactory> void container() throws Exception {

    final String containerResourceType = "geodemo/components/container";
    new MockUp<R>() {
      @Mock
      public ResourceResolver getResourceResolver(Map<String, Object> authInfo) {
        return context.resourceResolver();
      }
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