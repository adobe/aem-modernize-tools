package com.adobe.aem.modernize.servlet.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.servlet.RuleInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.jetbrains.annotations.NotNull;
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
  protected void beforeEach() {
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
    assertEquals("simple", ri.get("title").textValue());
    assertEquals(RULE_PATH + "/simple", ri.get("path").textValue());

    ri = ruleInfos.next();
    assertEquals("copyChildren", ri.get("title").textValue());
    assertEquals(RULE_PATH + "/copyChildren", ri.get("path").textValue());
  }

  private class MockListRulesServlet extends AbstractListRulesServlet {

    @NotNull
    @Override
    protected List<RuleInfo> getRules(@NotNull ResourceResolver rr, @NotNull Set<String> resourceTypes) {
      List<RuleInfo> rules = new ArrayList<>();
      rules.add(new RuleInfo(RULE_PATH + "/simple", "simple"));
      rules.add(new RuleInfo(RULE_PATH + "/copyChildren", "copyChildren"));
      return rules;
    }
  }
}
