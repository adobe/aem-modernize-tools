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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.MockRule;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class AbstractListRulesServletTest {

  private static final String RULE_PATH = "/apps/rules/component";
  private static final String CONTENT_PATH = "/content/component";
  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  private final MockListRulesServlet servlet = new MockListRulesServlet();

  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", CONTENT_PATH);
  }

  @Test
  public void invalidRequests() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    // No Paths
    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Rule list empty");

    // Path doesn't exist
    Map<String, Object> params = new HashMap<>();
    params.put("path", new String[] {"/does/not/exist"});
    request.setParameterMap(params);
    response.reset();

    servlet.doPost(request, response);
    result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Rule list empty");

    // Path no resource type
    params = new HashMap<>();
    params.put("path", CONTENT_PATH);
    request.setParameterMap(params);
    response.reset();

    servlet.doPost(request, response);
    result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Rule list empty");

  }

  @Test
  public void listRules() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Map<String, Object> params = new HashMap<>();
    params.put("path", new String[] {CONTENT_PATH + "/jcr:content/simple", CONTENT_PATH + "/copyChildren"});
    request.setParameterMap(params);


    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());
    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertTrue(ruleInfos.hasNext(), "Rule list populated");

    JsonNode ri = ruleInfos.next();
    assertEquals(RULE_PATH + "/simple", ri.get("title").textValue());
    assertEquals(RULE_PATH + "/simple", ri.get("id").textValue());

    ri = ruleInfos.next();
    assertEquals(RULE_PATH + "/copyChildren", ri.get("title").textValue());
    assertEquals(RULE_PATH + "/copyChildren", ri.get("id").textValue());
  }

  private class MockListRulesServlet extends AbstractListRulesServlet {

    @Override
    protected @NotNull RewriteRuleService getRewriteRuleService() {
      return new RewriteRuleService() {
        @Override
        public @NotNull Set<String> find(@NotNull Resource resource) {
          return null;
        }

        @Override
        public @NotNull Set<RewriteRule> listRules(@NotNull ResourceResolver resourceResolver, String... slingResourceType) {
          Set<RewriteRule> rules = new HashSet<>();
          rules.add(new MockRule(RULE_PATH + "/simple"));
          rules.add(new MockRule(RULE_PATH + "/copyChildren"));
          return rules;
        }

        @Override
        public @NotNull Set<String> listRules(@NotNull Resource resource) {
          return null;
        }

        @Override
        public @Nullable RewriteRule getRule(@NotNull ResourceResolver resourceResolver, @NotNull String id) {
          return null;
        }
      };
    }
  }
}
