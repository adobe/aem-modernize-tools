package com.adobe.aem.modernize.impl;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Value;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class RewriteUtilsTest {

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  @BeforeEach
  public void beforeEach() {
    context.load().json("/rewrite/test-references.json", "/content/test");
  }

  @Test
  public void updateReferences() throws Exception {
    String replacement = "/content/test";
    String subpath = "/subpath";
    Node root = context.resourceResolver().getResource("/content/test").adaptTo(Node.class);
    RewriteUtils.updateReferences(root, "/content/original", replacement);

    Node node = context.resourceResolver().getResource("/content/test/simple").adaptTo(Node.class);

    Property property = node.getProperty("literal");
    assertEquals(replacement, property.getString(), "Literal value");
    property = node.getProperty("literalSubpath");
    assertEquals(replacement + subpath, property.getString(), "Subpath Literal value");

    property = node.getProperty("list");
    Value[] values = property.getValues();
    assertEquals(replacement, values[0].getString(), "List literal value");
    assertEquals(replacement + subpath, values[1].getString(), "List subpath literal value");

    property = node.getProperty("richText");
    assertEquals("<a href=&quot;/content/test&quot>Link</a>", property.getString(), "Rich Text Value");
    property = node.getProperty("richTextLongerPath");
    assertEquals("<a href=&quot;/content/test/subpath&quot>Link</a>", property.getString(), "Longer path Rich Text Value");

    node = node.getNode("nested");
    property = node.getProperty("literal");
    assertEquals(replacement, property.getString(), "Literal value");
    property = node.getProperty("literalSubpath");
    assertEquals(replacement + subpath, property.getString(), "Subpath Literal value");

    property = node.getProperty("list");
    values = property.getValues();
    assertEquals("/do/not/touch", values[0].getString(), "Untouched list value");
    assertEquals(replacement, values[1].getString(), "List literal value");
    assertEquals(replacement + subpath, values[2].getString(), "List subpath literal value");
    assertEquals("/do/not/touch/subpath", values[3].getString(), "Untouched list subpath value");

    property = node.getProperty("richText");
    assertEquals("<a href=&quot;/content/test&quot>Link</a>", property.getString(), "Rich Text Value");
    property = node.getProperty("richTextLongerPath");
    assertEquals("<a href=&quot;/content/test/subpath&quot>Link</a>", property.getString(), "Longer path Rich Text Value");
  }

  @Test
  public void calcNewPath() {
    assertEquals("/content/foo/bar", RewriteUtils.calcNewPath("/not/foo/bar", "/content"));
    assertEquals("/content/foo/bar/baz", RewriteUtils.calcNewPath("/not/foo/bar/baz", "/content"));
  }
}
