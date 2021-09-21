package com.adobe.aem.modernize.rule.impl;

import javax.jcr.Node;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.rule.RewriteRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class NodeBasedRewriteRuleTest {

  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  private static final String CONTENT_ROOT = "/content/test";
  private static final String RULES_ROOT = "/apps/test/rules";

  @BeforeEach
  private void beforeEach() {
    context.load().json("/rewrite/test-content.json", CONTENT_ROOT);

  }


  @Test
  public void testDoesNotMatch() throws Exception {
    context.load().json("/rewrite/test-negative-rules.json", RULES_ROOT);
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + "/simple").adaptTo(Node.class);

    // Replacement Checks
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/noReplacement").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/replacementIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/replacementIsEmpty").adaptTo(Node.class));
    assertFalse(rule.matches(content));

    // Patterns Checks
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/noPatterns").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/patternsIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/patternsEmpty").adaptTo(Node.class));
    assertFalse(rule.matches(content));

    // Aggregate Checks
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregateIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregatePatternsIsProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregateEmpty").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregatePatternsEmpty").adaptTo(Node.class));
    assertFalse(rule.matches(content));

    // Simple Wrong Primary Type
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/wrongPrimaryType").adaptTo(Node.class));
    assertFalse(rule.matches(content));


    // Simple Properties Wrong
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/missingProperty").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/wrongPropertyValue").adaptTo(Node.class));
    assertFalse(rule.matches(content));

    // Simple Tree Matching
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/missingChild").adaptTo(Node.class));
    assertFalse(rule.matches(content));

    // Tree checks
    content = rr.getResource(CONTENT_ROOT + "/simpleTree").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/missingGrandChild").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/wrongChildNodeType").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/wrongChildPropertyValue").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/wrongGrandChildNodeType").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/wrongGrandChildPropertyValue").adaptTo(Node.class));
    assertFalse(rule.matches(content));


    // Aggregate negative checks
    content = rr.getResource(CONTENT_ROOT + "/aggregate/title").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregateNotMatched").adaptTo(Node.class));
    assertFalse(rule.matches(content));
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregateIntermediateNode").adaptTo(Node.class));
    assertFalse(rule.matches(content));

    content = rr.getResource(CONTENT_ROOT + "/aggregate/simple").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregateLongerThanNodes").adaptTo(Node.class));
    assertFalse(rule.matches(content));

  }

  @Test
  public void testMatches() throws Exception {
    context.load().json("/rewrite/test-simple-rules.json", RULES_ROOT + "/simple");
    ResourceResolver rr = context.resourceResolver();
    Node content = rr.getResource(CONTENT_ROOT + "/simple").adaptTo(Node.class);
    RewriteRule rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/simple/simple").adaptTo(Node.class));
    assertTrue(rule.matches(content));

    content = rr.getResource(CONTENT_ROOT + "/rewriteOptional").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/simple/rewriteOptional").adaptTo(Node.class));
    assertTrue(rule.matches(content));

    content = rr.getResource(CONTENT_ROOT + "/nestedRewriteOptional").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/simple/nestedRewriteOptional").adaptTo(Node.class));
    assertTrue(rule.matches(content));

    context.load().json("/rewrite/test-aggregate-rules.json", RULES_ROOT + "/aggregate");
    content = rr.getResource(CONTENT_ROOT + "/aggregate/simple").adaptTo(Node.class);
    rule = new NodeBasedRewriteRule(rr.getResource(RULES_ROOT + "/aggregate/aggregate").adaptTo(Node.class));
    assertTrue(rule.matches(content));
  }

}
