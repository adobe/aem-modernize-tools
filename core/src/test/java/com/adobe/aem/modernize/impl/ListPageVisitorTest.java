package com.adobe.aem.modernize.impl;

import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class ListPageVisitorTest {

  private final AemContext slingContext = new AemContext(ResourceResolverType.JCR_MOCK);

  @Tested
  private ListPageVisitor visitor;

  @BeforeEach
  protected void beforeEach() {
    slingContext.load().json("/servlet/page-content.json", "/content/test");
  }

  @Test
  public void testVisit() {
    Resource r = slingContext.resourceResolver().getResource("/content/test");
    visitor.accept(r);
    List<String> paths = visitor.getPaths();
    assertTrue(paths.contains("/content/test"), "Contains root page");
    assertTrue(paths.contains("/content/test/products"), "Contains products page");
    assertTrue(paths.contains("/content/test/products/triangle"), "Contains triangle page");
    assertTrue(paths.contains("/content/test/products/square"), "Contains square page");
    assertTrue(paths.contains("/content/test/products/circle"), "Contains circle page");
    assertFalse(paths.contains("/content/test/notapage"), "Doesn't contain not a page");
  }

  @Test
  public void testVisitDepth() {
    Resource r = slingContext.resourceResolver().getResource("/content/test");
    ListPageVisitor visitor = new ListPageVisitor(3);
    visitor.accept(r);
    List<String> paths = visitor.getPaths();
    assertTrue(paths.contains("/content/test"), "Contains root page");
    assertTrue(paths.contains("/content/test/products"), "Contains products page");
    assertFalse(paths.contains("/content/test/products/triangle"), "Doesn't contain triangle page");
    assertFalse(paths.contains("/content/test/products/square"), "Doesn't contain square page");
    assertFalse(paths.contains("/content/test/products/circle"), "Doesn't contain circle page");
    assertFalse(paths.contains("/content/test/notapage"), "Doesn't contain not a page");
  }
}
