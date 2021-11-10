package com.adobe.aem.modernize.policy.rule.impl;

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
  public void beforeEach() {
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
