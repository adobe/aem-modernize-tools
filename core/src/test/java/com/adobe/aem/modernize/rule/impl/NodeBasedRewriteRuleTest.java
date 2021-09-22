package com.adobe.aem.modernize.rule.impl;

import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Value;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

import static com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule.*;

@ExtendWith(SlingContextExtension.class)
public class NodeBasedRewriteRuleTest {

  private static final String CONTENT_ROOT = "/content/test";
  private static final String RULES_ROOT = "/apps/test/rules";
  private static final String NEGATIVE_ROOT = RULES_ROOT + "/negative";
  private static final String SIMPLE_ROOT = RULES_ROOT + "/simple";
  private static final String AGGREGATE_ROOT = RULES_ROOT + "/aggregate";
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  @BeforeEach
  public void beforeEach() {
    context.load().json("/rewrite/test-content.json", CONTENT_ROOT);
    context.load().json("/rewrite/test-negative-rules.json", NEGATIVE_ROOT);
    context.load().json("/rewrite/test-simple-rules.json", SIMPLE_ROOT);
    context.load().json("/rewrite/test-aggregate-rules.json", AGGREGATE_ROOT);
  }

  @Test
  public void testRanking() throws Exception {
    ResourceResolver rr = context.resourceResolver();
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + "/rewriteRanking").adaptTo(Node.class));
    assertEquals(3, rule.getRanking(), "Provided ranking match");

    rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + "/simple").adaptTo(Node.class));
    assertEquals(Integer.MAX_VALUE, rule.getRanking(), "Default ranking match");

    rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + "/remove").adaptTo(Node.class));
    assertEquals(Integer.MAX_VALUE, rule.getRanking(), "Invalid ranking match");
  }

  @Test
  public void testDoesNotMatch() throws Exception {
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + "/simple").adaptTo(Node.class);

    // Replacement Checks
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/noReplacement").adaptTo(Node.class));
    assertFalse(rule.matches(content), "No replacement.");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/replacementIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Replacement node is property.");

    // Patterns Checks
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/noPatterns").adaptTo(Node.class));
    assertFalse(rule.matches(content), "No patterns specified.");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/patternsIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Patterns is property.");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/patternsEmpty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Patterns is empty.");

    // Aggregate Checks
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/aggregateIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Aggregate is property.");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/aggregatePatternsIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Aggregate Patterns is property.");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/aggregateEmpty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Aggregates is empty.");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/aggregatePatternsEmpty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Aggregates patterns is empty.");

    // Simple Wrong Primary Type
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/wrongPrimaryType").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Wrong primary type.");

    // Simple Properties Wrong
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/missingProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Missing property");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/wrongPropertyValue").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Wrong property value");

    // Simple Tree Matching
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/missingChild").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Missing child");

    // Tree checks
    content = rr.getResource(CONTENT_ROOT + "/simpleTree").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/missingGrandChild").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Missing Grandchild");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/wrongChildNodeType").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Wring child node type");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/wrongChildPropertyValue").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Wrong child property value");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/wrongGrandChildNodeType").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Wrong grandchild node type");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/wrongGrandChildPropertyValue").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Wrong grandchild property value");

    // Aggregate negative checks
    content = rr.getResource(CONTENT_ROOT + "/aggregate/title").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/aggregateNotMatched").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Aggregate not matched");
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/aggregateIntermediateNode").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Aggregate intermediate node exists");

    content = rr.getResource(CONTENT_ROOT + "/aggregate/simple").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/aggregateLongerThanNodes").adaptTo(Node.class));
    assertFalse(rule.matches(content), "Aggregate longer than content");
  }

  @Test
  public void testMatches() throws Exception {
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + "/simple").adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + "/simple").adaptTo(Node.class));
    assertTrue(rule.matches(content), "Simple comparison");

    content = rr.getResource(CONTENT_ROOT + "/rewriteOptional").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + "/rewriteOptional").adaptTo(Node.class));
    assertTrue(rule.matches(content), "Rewrite optional node");

    content = rr.getResource(CONTENT_ROOT + "/nestedRewriteOptional").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + "/nestedRewriteOptional").adaptTo(Node.class));
    assertTrue(rule.matches(content), "Nested rewrite optional");

    content = rr.getResource(CONTENT_ROOT + "/aggregate/title").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(AGGREGATE_ROOT + "/aggregate").adaptTo(Node.class));
    assertTrue(rule.matches(content), "Simple aggregate match");
  }

  @Test
  public void testReplacementRemoved() throws Exception {
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + "/remove").adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(NEGATIVE_ROOT + "/noReplacement").adaptTo(Node.class));
    assertThrows(RewriteException.class, () -> rule.applyTo(content, new HashSet<>()), "Throws when missing replacement node");
  }

  @Test
  public void testRemoveApplyTo() throws Exception {
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + "/remove").adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + "/remove").adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    assertNull(rr.getResource(CONTENT_ROOT + "/remove"), "Node was removed");

    content = rr.getResource(CONTENT_ROOT + "/aggregate/title").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(AGGREGATE_ROOT + "/remove").adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    assertNull(rr.getResource(CONTENT_ROOT + "/aggregate/title"), "Aggregate title node was removed");
    assertNull(rr.getResource(CONTENT_ROOT + "/aggregate/text"), "Aggregate text node was removed");
    assertNull(rr.getResource(CONTENT_ROOT + "/aggregate/image"), "Aggregate image node was removed");

  }

  @Test
  public void testSimpleApplyTo() throws Exception {
    final String nodePath = "/simple";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertEquals("core/wcm/components/title/v2/title", updated.getProperty("sling:resourceType").getString(), "Property was upadated");
  }

  @Test
  public void testCopyChildrenApplyTo() throws Exception {
    final String nodePath = "/copyChildren";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertFalse(updated.hasProperty(PN_CQ_COPY_CHILDREN), "Directive property removed");
    assertNotNull(updated.getNode("items"), "Items child was copied");
    assertNotNull(updated.getNode("header"), "Header child was copied");
  }

  @Test
  public void testCopyChildrenNestedApplyTo() throws Exception {
    final String nodePath = "/copyChildrenNested";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertFalse(updated.getNode("parsys").hasProperty(PN_CQ_COPY_CHILDREN), "Directive property removed");
    assertNotNull(updated.getNode("parsys/items"), "Items child was copied");
    assertNotNull(updated.getNode("parsys/header"), "Header child was copied");
  }

  @Test
  public void testCopyChildrenOrderApplyTo() throws Exception {
    final String nodePath = "/copyChildrenOrder";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertFalse(updated.getNode("items").hasProperty(PN_CQ_ORDER_BEFORE), "Directive property removed");
    NodeIterator children = updated.getNodes();
    assertEquals("parsys", children.nextNode().getName(), "Correct order");
    assertEquals("items", children.nextNode().getName(), "Correct order");
    assertEquals("header", children.nextNode().getName(), "Correct order");
  }

  @Test
  public void testMapPropertiesApplyTo() throws Exception {
    final String nodePath = "/mapProperties";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);

    assertEquals("map-property-1", updated.getProperty("map-property-simple").getString(), "Simple mapped value");
    assertFalse(updated.getProperty("map-property-negation").getBoolean(), "Negated mapped value");
    assertEquals("map-property-3", updated.getProperty("map-property-nested").getString(), "Nested mapped value");
    assertFalse(updated.hasProperty("map-property-unknown"), "Bad source reference is removed.");
    assertFalse(updated.hasNode("items"), "Items not copied");
    assertEquals("default", updated.getProperty("map-property-default").getString(), "Default mapped value");
    assertEquals("default", updated.getProperty("map-property-default-quoted").getString(), "Quoted Default mapped value");
    assertEquals("default", updated.getProperty("map-property-default").getString(), "Default mapped value");

    Property property = updated.getProperty("map-property-ifelse");
    assertEquals("map-property-3", property.getString(), "IfElse value");
    property = updated.getProperty("map-property-default");
    assertEquals("default", property.getString(), "IfElse value default");
    property = updated.getProperty("map-property-default");
    assertFalse(property.getBoolean(), "IfElse value negation");

    property = updated.getProperty("map-property-multiple");
    assertTrue(property.isMultiple(), "Multiple property is");
    Value[] values = property.getValues();
    assertEquals("map-property-4-value-1", values[0].getString(), "Multiple value");
    assertEquals("map-property-4-value-2", values[1].getString(), "Multiple value");
  }

  @Test
  public void testRewriteMapChildrenApplyTo() throws Exception {
    final String nodePath = "/rewriteMapChildren";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertFalse(updated.getNode("items").hasProperty(PN_CQ_MAP_CHILDREN), "Directive property removed");
    assertFalse(updated.hasNode("notItems"), "NotItems not found.");
    assertTrue(updated.hasNode("items"), "Items found.");
    assertTrue(updated.hasNode("items/item1"), "Item child found.");
    assertTrue(updated.hasNode("items/item2"), "Item child found.");
  }

  @Test
  public void testRewriteMapChildrenNestedApplyTo() throws Exception {
    final String nodePath = "/rewriteMapChildrenNested";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    rule.applyTo(content, new HashSet<>());
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertFalse(updated.getNode("items/item2").hasProperty(PN_CQ_COPY_CHILDREN), "Directive property removed");
    assertFalse(updated.hasNode("items/not-item2"), "not-item2 not found.");
    assertTrue(updated.hasNode("items"), "Items found.");
    assertTrue(updated.hasNode("items/item1"), "Item child found.");
    assertTrue(updated.hasNode("items/item2"), "Item child found.");
  }

  @Test
  public void testRewriteFinalApplyTo() throws Exception {
    final String nodePath = "/rewriteFinal";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    Set<String> finalPaths = new HashSet<>();
    rule.applyTo(content, finalPaths);
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertFalse(updated.hasProperty(PN_CQ_COPY_CHILDREN), "Directive property removed");
    assertEquals(1, finalPaths.size(), "Final node list size");
    assertEquals(CONTENT_ROOT + nodePath, finalPaths.stream().findFirst().get(), "Final node path");
  }

  @Test
  public void testReplacementRewriteFinalApplyTo() throws Exception {
    final String nodePath = "/replacementRewriteFinal";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    Set<String> finalPaths = new HashSet<>();
    rule.applyTo(content, finalPaths);
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();
    assertEquals(2, finalPaths.size(), "Final node list size");
    assertTrue(finalPaths.contains(CONTENT_ROOT + nodePath), "Final root node path");
    assertTrue(finalPaths.contains(CONTENT_ROOT + nodePath + "/items"), "Final root node path");
  }

  @Test
  public void testRewritePropertiesApplyTo() throws Exception {
    final String nodePath = "/rewriteProperties";
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(SIMPLE_ROOT + nodePath).adaptTo(Node.class));
    Set<String> finalPaths = new HashSet<>();
    rule.applyTo(content, finalPaths);
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();

    Node updated = rr.getResource(CONTENT_ROOT + nodePath).adaptTo(Node.class);
    assertFalse(updated.hasNode(NN_CQ_REWRITE_PROPERTIES), "Directive node removed");

    assertEquals("token", updated.getProperty("rewrite-remove-prefix").getString(), "Prefix rewrite");
    assertEquals("token", updated.getProperty("rewrite-remove-suffix").getString(), "Suffix rewrite");
    assertEquals("token1token2", updated.getProperty("rewrite-concat-tokens").getString(), "Concat rewrite");
    assertEquals("prefix-token", updated.getProperty("rewrite-no-capture-use").getString(), "No capture use rewrite");
    assertTrue(updated.getProperty("rewrite-boolean").getBoolean(), "Boolean rewrite (not supported)");

  }

  @Test
  public void testAggregateApplyTo() throws Exception {
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + "/aggregate/title").adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(AGGREGATE_ROOT + "/aggregate").adaptTo(Node.class));
    Set<String> finalPaths = new HashSet<>();
    rule.applyTo(content, finalPaths);
    assertTrue(content.getSession().hasPendingChanges(), "Session has changes");
    content.getSession().save();

    Node updated = rr.getResource(CONTENT_ROOT + "/aggregate/title").adaptTo(Node.class);

    // Aggregate nodes were removed
    Node parent = updated.getParent();
    assertFalse(parent.hasNode("text"), "Title node removed");
    assertFalse(parent.hasNode("image"), "Image node removed");
    assertEquals("core/wcm/components/teaser/v1/teaser", updated.getProperty("sling:resourceType").getString(), "sling:resourceType value");
    assertEquals("Strategic Consulting", updated.getProperty("jcr:title").getString(), "jcr:title value");
    assertEquals("<p>Text of the Text Component</p>", updated.getProperty("jcr:description").getString(), "jcr:description value");
    assertTrue(updated.getProperty("textIsRich").getBoolean(), "textIsRich value");
    assertEquals("/content/dam/aem-modernize/portraits/jane_doe.jpg", updated.getProperty("fileReference").getString(), "fileReference value");
  }
}
