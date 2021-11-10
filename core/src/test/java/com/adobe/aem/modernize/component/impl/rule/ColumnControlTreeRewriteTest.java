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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.component.impl.ComponentRewriteRuleServiceImpl;
import mockit.Mock;
import mockit.MockUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(SlingContextExtension.class)
public class ColumnControlTreeRewriteTest {

  // Oak needed to verify order preservation.
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  @BeforeEach
  public void beforeEach() {
    context.load().json("/component/page-content.json", "/content/test");
    context.load().json("/component/code-content.json", "/apps");
    context.load().json("/component/test-rules.json", "/var/aem-modernize/rules/component");
  }


  @Test
  public <R extends ResourceResolver, F extends ResourceResolverFactory> void test() throws Exception {

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

    ComponentRewriteRuleServiceImpl componentService = new ComponentRewriteRuleServiceImpl();

    ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
    Map<String, Object> props = new HashMap<>();
    props.put("layout.value", "2;cq-colctrl-lt0");
    props.put("column.widths", new String[] {  "default=[3,3]", "tablet=[6,6]", "phone=[12,12]" });
    props.put("container.resourceType", "geodemo/components/container");
    context.registerInjectActivateService(rule, props);

    props = new HashMap<>();
    props.put("search.paths", new String[] {"/var/aem-modernize/rules/component"});
    context.registerInjectActivateService(componentService, props);

    Resource root = context.resourceResolver().getResource("/content/test/parentNotResponsiveGrid/jcr:content");
    Set<String> rules = new HashSet<>();
    rules.add("/var/aem-modernize/rules/component/geoText");
    rules.add("/var/aem-modernize/rules/component/parsys");
    rules.add(rule.getId());

    componentService.apply(root, rules,true);

    ResourceResolver rr = root.getResourceResolver();
    rr.commit();
    Resource updated = context.resourceResolver().getResource("/content/test/parentNotResponsiveGrid/jcr:content");
    Resource par = updated.getChild("par");
    assertNotNull(par, "Responsive Grid Exists");
    assertEquals("geodemo/components/container", par.getResourceType(), "Responsive grid resource type");

    Iterator<Resource> children = par.listChildren();
    assertEquals("title", children.next().getName(), "Node order preserved.");
    assertEquals("image_1", children.next().getName(), "Node order preserved.");
    assertEquals("title_1", children.next().getName(), "Node order preserved.");
    assertEquals("text_1", children.next().getName(), "Node order preserved.");
    assertEquals("image_0", children.next().getName(), "Node order preserved.");
    assertEquals("title_2", children.next().getName(), "Node order preserved.");
    assertEquals("text_0", children.next().getName(), "Node order preserved.");
    assertEquals("image", children.next().getName(), "Node order preserved.");
  }
}
