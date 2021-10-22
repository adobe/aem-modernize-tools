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
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.RewriteRuleMapping;
import com.day.cq.wcm.api.designer.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class ListPolicyRulesServletTest {

  private static final String CONTENT_PATH = "/content/policy";
  private static final String DESIGN_PATH = "/etc/design/policy";

  private final AemContext aemContext = new AemContext(ResourceResolverType.JCR_MOCK);

  @Injectable
  private PolicyImportRuleService importRuleService;

  @Tested
  private ListPolicyRulesServlet servlet;

  @BeforeEach
  protected void beforeEach() {
    aemContext.load().json("/policy/all-content.json", CONTENT_PATH);
    aemContext.load().json("/policy/all-designs.json", DESIGN_PATH);
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
    assertFalse(ruleInfos.hasNext(), "Policy Rule list empty");

    // Path doesn't exist
    Map<String, Object> params = new HashMap<>();
    params.put("path", "/does/not/exist");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Policy Rule list empty");

    // Path not page
    params = new HashMap<>();
    params.put("path", CONTENT_PATH + "/notapage");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Policy Rule list empty");
  }

  @Test
  public <S extends Style> void policyResourceListEmpty() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    final String path = CONTENT_PATH + "/noDesignPath";
    Map<String, Object> params = new HashMap<>();
    params.put("path", path);
    request.setParameterMap(params);

    final Set<RewriteRuleMapping> mappings = Collections.emptySet();
    final List<Resource> capture = new ArrayList<>();
    new Expectations() {{
      importRuleService.find(withCapture(capture));
      result = mappings;
    }};

    new MockUp<S>() {
      @Mock
      public String getPath() {
        return DESIGN_PATH + "/jcr:content/homepage";
      }
    };

    servlet.doGet(request, response);

    assertEquals(1, capture.size(), "Policy API calls");
    assertEquals(DESIGN_PATH + "/jcr:content/homepage", capture.get(0).getPath(), "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Policy Rule list empty");
  }

  @Test
  public <S extends Style> void policyRules() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    final String path = CONTENT_PATH + "/directDesignPath";
    Map<String, Object> params = new HashMap<>();
    params.put("path", path);
    request.setParameterMap(params);

    final Set<String> paths = new HashSet<>();
    paths.add(DESIGN_PATH + "/jcr:content/homepage/par/title");

    final Set<RewriteRule> rules = new HashSet<>();
    rules.add(new MockRule("/apps/rules/policy/title"));

    final List<Resource> capture = new ArrayList<>();
    new Expectations() {{
      importRuleService.find(withCapture(capture));
      result = paths;
      importRuleService.listRules(withInstanceOf(ResourceResolver.class), withNotNull());
      result = rules;
    }};

    new MockUp<S>() {
      @Mock
      public String getPath() {
        return DESIGN_PATH + "/jcr:content/homepage";
      }
    };

    servlet.doGet(request, response);

    assertEquals(1, capture.size(), "Policy API calls");
    assertEquals(DESIGN_PATH + "/jcr:content/homepage", capture.get(0).getPath(), "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertTrue(ruleInfos.hasNext(), "Policy Rule list populated");

    JsonNode ri = ruleInfos.next();
    assertEquals("/apps/rules/policy/title", ri.get("title").textValue());
    assertEquals("/apps/rules/policy/title", ri.get("path").textValue());

    assertFalse(ruleInfos.hasNext(), "RuleInfo length");

  }
}
