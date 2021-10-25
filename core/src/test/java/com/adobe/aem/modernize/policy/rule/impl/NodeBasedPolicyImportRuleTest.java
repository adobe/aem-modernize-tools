package com.adobe.aem.modernize.policy.rule.impl;

import java.util.HashSet;
import javax.jcr.Node;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class NodeBasedPolicyImportRuleTest {

  private static final String RULES_ROOT = "/apps/aem-modernize/policy/rules";

  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  @BeforeEach
  protected void beforeEach() {
    context.load().json("/policy/test-rules.json", RULES_ROOT);
    context.load().json("/policy/all-designs.json", "/etc/designs/test");
  }

  @Test
  public void testApplyTo() throws Exception {

    final String path = "/etc/designs/test/jcr:content/homepage/title";
    Node ruleNode = context.resourceResolver().getResource(RULES_ROOT + "/simple").adaptTo(Node.class);
    NodeBasedPolicyImportRule rule = new NodeBasedPolicyImportRule(ruleNode);

    Node title = context.resourceResolver().getResource(path).adaptTo(Node.class);
    Node result = rule.applyTo(title, new HashSet<>());

    Resource original = context.resourceResolver().getResource(path);
    assertNotNull(original, "Original node preserved");
    assertNotEquals(result.getPath(), path, "New node has new path");

    assertEquals("geometrixx/components/title", original.getResourceType(), "Original state preserved");
    assertEquals("core/wcm/components/title/v2/title", result.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Update occured");
  }

}
