package com.adobe.aem.modernize.servlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.MockRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
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
public class ListComponentRulesServletTest {

  private static final String RULE_PATH = "/apps/rules/component";
  private static final String CONTENT_PATH = "/content/component";
  private final AemContext aemContext = new AemContext(ResourceResolverType.JCR_MOCK);

  @Injectable
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Tested
  private ListComponentRulesServlet servlet;

  @BeforeEach
  protected void beforeEach() {
    aemContext.load().json("/component/all-content.json", CONTENT_PATH);
  }

  @Test
  public void invalidJobData() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    // No Path
    servlet.doGet(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Component Rule list empty");

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/does/not/exist");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Component Rule list empty");

    // Path Not page
    params = new HashMap<>();
    params.put("path", CONTENT_PATH + "/jcr:content/simple");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Component Rule list empty");
  }

  @Test
  public void componentResourceListEmpty() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    final String path = CONTENT_PATH;
    Map<String, Object> params = new HashMap<>();
    params.put("path", path);
    request.setParameterMap(params);

    final Set<String> resources = Collections.emptySet();
    final List<Resource> capture = new ArrayList<>();
    new Expectations() {{
      componentRewriteRuleService.find(withCapture(capture));
      result = resources;
    }};

    servlet.doGet(request, response);

    assertEquals(1, capture.size(), "Component API calls");
    assertEquals(path + "/jcr:content", capture.get(0).getPath(), "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Component Rule list empty");
  }

  @Test
  public void componentRules() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    final String path = CONTENT_PATH;
    Map<String, Object> params = new HashMap<>();
    params.put("path", path);
    request.setParameterMap(params);

    final Set<String> resources = new HashSet<>();
    resources.add(CONTENT_PATH + "/jcr:content/simple");
    resources.add(CONTENT_PATH + "/jcr:content/copyChildren");

    final Set<RewriteRule> rules = new HashSet<>();
    rules.add(new MockRule(RULE_PATH + "/simple"));
    rules.add(new MockRule(RULE_PATH + "/copyChildren"));

    final List<Resource> capture = new ArrayList<>();
    new Expectations() {{
      componentRewriteRuleService.find(withCapture(capture));
      result = resources;
      componentRewriteRuleService.listRules(withInstanceOf(ResourceResolver.class), withNotNull());
      result = rules;
    }};

    servlet.doGet(request, response);

    assertEquals(1, capture.size(), "Component API calls");
    assertEquals(path + "/jcr:content", capture.get(0).getPath(), "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertTrue(ruleInfos.hasNext(), "Component Rule list populated");

    JsonNode ri = ruleInfos.next();
    assertEquals(RULE_PATH + "/simple", ri.get("title").textValue());
    assertEquals(RULE_PATH + "/simple", ri.get("path").textValue());

    ri = ruleInfos.next();
    assertEquals(RULE_PATH + "/copyChildren", ri.get("title").textValue());
    assertEquals(RULE_PATH + "/copyChildren", ri.get("path").textValue());

    assertFalse(ruleInfos.hasNext(), "RuleInfo length");

  }
}
