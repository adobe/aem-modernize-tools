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

}
