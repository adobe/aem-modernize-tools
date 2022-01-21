package com.adobe.aem.modernize.component.impl;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

// This is here to prevent the other tests from taking longer as this needs an actual Oak impl.
@ExtendWith(SlingContextExtension.class)
public class ComponentRewriteRuleServiceOakTest {

  private final String[] RULE_PATHS = new String[] { "/apps/aem-modernize/component/rules", "/apps/customer/component/rules" };
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  private final ComponentRewriteRuleService componentRewriteRuleService = new ComponentRewriteRuleServiceImpl();

  @BeforeEach
  public void beforeEach() {
    Map<String, Object> props = new HashMap<>();
    props.put("search.paths", RULE_PATHS);
    context.load().json("/component/test-rules.json", "/apps/aem-modernize/component/rules");
    context.load().json("/component/all-content.json", "/content/test/all");
    context.load().json("/component/aggregate-content.json", "/content/test/aggregate");
    context.registerInjectActivateService(componentRewriteRuleService, props);
  }

  @Test
  public void testIssue_119_beginning() throws Exception {
    Resource resource = context.resourceResolver().getResource("/content/test/all/jcr:content/simple");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/deletes_simple");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/all/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/copyChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/mapProperties", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteOptional", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteMapChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinal", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinalOnReplacement", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteProperties", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");
  }

  @Test
  public void testIssue_119_middle() throws Exception {
    Resource resource = context.resourceResolver().getResource("/content/test/all/jcr:content/rewriteOptional");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/deletes_middle");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/all/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/simple", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/mapProperties", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteMapChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinal", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinalOnReplacement", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteProperties", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");
  }

  @Test
  public void testIssue_119_end() throws Exception {
    Resource resource = context.resourceResolver().getResource("/content/test/all/jcr:content/rewriteProperties");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/deletes_end");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/all/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/simple", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/mapProperties", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteOptional", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteMapChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinal", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinalOnReplacement", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");
  }

  @Test
  public void testShallowKeepsOrder() throws Exception {
    Resource resource = context.resourceResolver().getResource("/content/test/all/jcr:content/copyChildren");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/copyChildren");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/all/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/simple", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/mapProperties", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteOptional", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteMapChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinal", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinalOnReplacement", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteProperties", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");
  }

  @Test
  public void testShallowAggregateKeepsOrder() throws Exception {
    Resource resource = context.resourceResolver().getResource("/content/test/all/jcr:content/copyChildrenOrder");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/aggregate");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/all/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/simple", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteMapChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinal", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinalOnReplacement", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteProperties", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");
  }

  @Test
  public void testShallowAggregateOnlyChild() throws Exception {
    Resource resource = context.resourceResolver().getResource("/content/test/aggregate/singleNode/jcr:content/copyChildrenOrder");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/aggregate");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/aggregate/singleNode/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");
  }

  @Test
  public void testShallowAggregateFirstNode() throws Exception {
    Resource resource = context.resourceResolver().getResource("/content/test/aggregate/singleSibling/jcr:content/copyChildrenOrder");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/aggregate");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/aggregate/singleSibling/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");
  }
}
