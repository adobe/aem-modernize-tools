package com.adobe.aem.modernize.servlet.rule;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.MockRule;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.adobe.aem.modernize.structure.impl.rule.PageRewriteRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class ListTemplateRulesServletTest {
  private final AemContext context = new AemContext(ResourceResolverType.JCR_OAK);

  private static final String CONTENT_PATH = "/content/test";

  @Injectable
  private StructureRewriteRuleService structureRuleService;

  @Tested
  private ListTemplateRulesServlet servlet;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", "/content/test");
  }

  @Test
  public void testTemplateRules() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", CONTENT_PATH + "/jcr:content");
    request.setParameterMap(params);


    final Set<RewriteRule> rules = new HashSet<>();
    rules.add(new MockRule(PageRewriteRule.class.getName()));
    List<String[]> capture = new ArrayList<>();

    new Expectations() {{
      structureRuleService.listRules(withInstanceOf(ResourceResolver.class), withCapture(capture));
      result = rules;
    }};

    servlet.doPost(request, response);
    assertEquals(1, capture.size(), "Structure API calls");
    assertEquals("aem-modernize/components/homepage", capture.get(0)[0], "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertTrue(ruleInfos.hasNext(), "Component Rule list populated");

    JsonNode ri = ruleInfos.next();
    assertEquals(PageRewriteRule.class.getName(), ri.get("title").textValue(), "Rule title");
    assertEquals(PageRewriteRule.class.getName(), ri.get("id").textValue(), "Rule ID");

    assertFalse(ruleInfos.hasNext(), "RuleInfo length");
  }

  @Test
  public void testTemplateRulesReprocessNoRevision() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", CONTENT_PATH + "/jcr:content");
    params.put("reprocess", true);
    request.setParameterMap(params);

    final Set<RewriteRule> rules = new HashSet<>();
    rules.add(new MockRule(PageRewriteRule.class.getName()));
    List<String[]> capture = new ArrayList<>();

    new Expectations() {{
      structureRuleService.listRules(withInstanceOf(ResourceResolver.class), withCapture(capture));
      result = rules;
    }};

    servlet.doPost(request, response);
    assertEquals(1, capture.size(), "Structure API calls");
    assertEquals("aem-modernize/components/homepage", capture.get(0)[0], "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertTrue(ruleInfos.hasNext(), "Component Rule list populated");

    JsonNode ri = ruleInfos.next();
    assertEquals(PageRewriteRule.class.getName(), ri.get("title").textValue(), "Rule title");
    assertEquals(PageRewriteRule.class.getName(), ri.get("id").textValue(), "Rule ID");

    assertFalse(ruleInfos.hasNext(), "RuleInfo length");
  }

}
