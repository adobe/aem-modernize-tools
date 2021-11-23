package com.adobe.aem.modernize.component.rule;

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

import java.util.HashSet;
import javax.jcr.Node;
import javax.jcr.NodeIterator;

import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ParsysComponentNodeBasedRuleTest {
  // Oak needed to verify order preservation.
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  @BeforeEach
  public void beforeEach() {
    context.load().json("/component/page-content.json", "/content/test");
    context.load().json("/component/test-rules.json", "/apps/aem-modernize/component/rules");
  }

  @Test
  public void testConversion() throws Exception {

    Node ruleNode = context.resourceResolver().getResource("/apps/aem-modernize/component/rules/parsys").adaptTo(Node.class);
    NodeBasedRewriteRule rule = new NodeBasedRewriteRule(ruleNode);

    Node parsys = context.resourceResolver().getResource("/content/test/doesNotMatch/jcr:content/par").adaptTo(Node.class);

    assertTrue(rule.matches(parsys), "Rule Matches");
    Node result = rule.applyTo(parsys, new HashSet<>());
    result.getSession().save();
    result = context.resourceResolver().getResource("/content/test/doesNotMatch/jcr:content/par").adaptTo(Node.class);
    assertEquals("geodemo/components/container", result.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Resource Type");
    assertEquals("responsiveGrid", result.getProperty("layout").getString(), "Layout Type");
    NodeIterator children = result.getNodes();
    assertEquals("colctrl", children.nextNode().getName(), "Order preserved");
    assertEquals("image", children.nextNode().getName(), "Order preserved");
    assertEquals("title_1", children.nextNode().getName(), "Order preserved");
    assertEquals("text_1", children.nextNode().getName(), "Order preserved");
    assertEquals("col_break", children.nextNode().getName(), "Order preserved");
    assertEquals("image_0", children.nextNode().getName(), "Order preserved");
    assertEquals("title_2", children.nextNode().getName(), "Order preserved");
    assertEquals("text_0", children.nextNode().getName(), "Order preserved");
    assertEquals("col_break_1", children.nextNode().getName(), "Order preserved");
    assertEquals("image_3", children.nextNode().getName(), "Order preserved");
    assertEquals("title_3", children.nextNode().getName(), "Order preserved");
    assertEquals("text_3", children.nextNode().getName(), "Order preserved");
    assertEquals("col_end", children.nextNode().getName(), "Order preserved");
    assertFalse(children.hasNext(), "Order preserved");

  }

}
