package com.adobe.aem.modernize.impl;

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

import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class ListPageVisitorTest {

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  @Tested
  private ListPageVisitor visitor;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", "/content/test");
  }

  @Test
  public void testVisit() {
    Resource r = context.resourceResolver().getResource("/content/test");
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
    Resource r = context.resourceResolver().getResource("/content/test");
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
